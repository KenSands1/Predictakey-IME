package com.predictivekb.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

class PredictiveKeyboardService : InputMethodService(), KeyboardActionListener {

    companion object {
        /** Punctuation that attaches directly to the previous word, pulling
         * a trailing space back first. Digits and other symbols don't. */
        private val ATTACHING_PUNCTUATION = setOf('.', ',', '!', '?', ':', ';', ')')
    }

    private lateinit var container: FrameLayout
    private lateinit var lettersPanel: KeyboardPanelView
    private lateinit var symbolsPanel: SymbolKeyboardView
    private lateinit var macroPanel: MacroKeyboardView
    private lateinit var macroStore: MacroStore
    private lateinit var statsStore: StatsStore

    private val engine = PredictionEngine()
    private val currentWord = StringBuilder()
    private var showingSymbols = false
    private var currentMacroPage = 0

    /**
     * Remembers that the CURRENT word started under SHIFT_ONCE capitalize
     * intent, even after the shift key itself has already reverted to OFF.
     * See effectiveShiftStateForCompletion() for why this exists.
     */
    private var wordStartCapitalized = false

    /**
     * Non-null when the top row is showing a specific root word's family
     * members (e.g. "work" -> work/works/working/worked) rather than
     * top-level root-word suggestions. Set when a root is tapped; cleared
     * by any action other than tapping a family member - typing a letter,
     * hitting space, backspace, punctuation, or enter all "go back to the
     * start" per the intended design.
     */
    private var activeRootFamily: String? = null

    /** How many characters (word + trailing space) to delete if the swap window's tapped. */
    private var overwriteLength = 0

    override fun onCreate() {
        super.onCreate()
        if (!engine.isLoaded()) {
            assets.open("wordlist.txt").use { engine.load(it) }
        }
        macroStore = MacroStore(this)
        statsStore = StatsStore(this)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Make sure a session isn't left dangling un-recorded once the
        // keyboard's dismissed - otherwise stats viewed right after typing
        // wouldn't reflect the session that just happened.
        statsStore.flushSession()
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this)

        lettersPanel = KeyboardPanelView(this).apply { listener = this@PredictiveKeyboardService }
        symbolsPanel = SymbolKeyboardView(this).apply { listener = this@PredictiveKeyboardService }
        macroPanel = MacroKeyboardView(this).apply { listener = this@PredictiveKeyboardService }

        container.addView(lettersPanel)
        container.addView(symbolsPanel)
        container.addView(macroPanel)
        showLetters()
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        wordStartCapitalized = false
        activeRootFamily = null
        showLetters()
        refreshPredictions()
    }

    // ---- panel switching --------------------------------------------------

    private fun showLetters() {
        showingSymbols = false
        lettersPanel.visibility = View.VISIBLE
        symbolsPanel.visibility = View.GONE
        macroPanel.visibility = View.GONE
    }

    private fun showSymbols() {
        showingSymbols = true
        lettersPanel.visibility = View.GONE
        symbolsPanel.visibility = View.VISIBLE
        macroPanel.visibility = View.GONE
    }

    private fun showMacros() {
        showingSymbols = false
        lettersPanel.visibility = View.GONE
        symbolsPanel.visibility = View.GONE
        macroPanel.visibility = View.VISIBLE
    }

    override fun onSwitchToSymbols() = showSymbols()
    override fun onSwitchToLetters() = showLetters()

    override fun onSwitchToMacros() {
        showMacros()
        refreshMacroPanel()
    }

    override fun onNextMacroPage() {
        val count = macroStore.pageCount()
        currentMacroPage = (currentMacroPage + 1) % count
        refreshMacroPanel()
    }

    private fun refreshMacroPanel() {
        val macros = macroStore.macrosForPage(currentMacroPage)
        macroPanel.showPage(macros, currentMacroPage + 1, macroStore.pageCount())
    }

    // ---- prediction refresh -------------------------------------------

    private fun refreshPredictions() {
        maybeAutoCapitalize()
        val rtl = Prefs.isPredictionRowRtl(this)
        val prefix = currentWord.toString()
        val family = activeRootFamily
        // Frequency order, most to least common - no re-sorting for display.
        val completions = if (family != null) {
            engine.familyMembers(family).filterNot { it == family }
        } else {
            engine.topCompletions(prefix)
        }
        val wordsWithFamily = completions.filter { engine.hasFamilyVariants(it) }.toSet()
        lettersPanel.updateWordCompletions(completions, rtl, wordsWithFamily)
    }

    /**
     * Arms SHIFT_ONCE whenever the cursor sits at the start of a sentence -
     * an empty field, or after ". "/"! "/"? "/a newline (ignoring trailing
     * spaces). Only acts when shift is currently OFF, so it never overrides
     * a state the user (or this same check, earlier) already set.
     */
    private fun maybeAutoCapitalize() {
        if (lettersPanel.getShiftState() != ShiftState.OFF) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val trimmed = before.trimEnd(' ', '\t')
        val shouldCap = trimmed.isEmpty() || trimmed.last() in charArrayOf('.', '!', '?', '\n')
        if (shouldCap) {
            lettersPanel.setShiftState(ShiftState.SHIFT_ONCE)
        }
    }

    /**
     * What casing a word-completion (button tap or space-bar sole
     * completion) should use. Live CAPS_LOCK always wins (it's never
     * "consumed", so it's always accurate). Otherwise, if this word started
     * under SHIFT_ONCE - even if that's since been consumed - treat it as
     * SHIFT_ONCE for casing purposes.
     */
    private fun effectiveShiftStateForCompletion(): ShiftState {
        val live = lettersPanel.getShiftState()
        return when {
            live == ShiftState.CAPS_LOCK -> ShiftState.CAPS_LOCK
            wordStartCapitalized -> ShiftState.SHIFT_ONCE
            else -> live
        }
    }

    /** Records capitalize-intent for the word about to start, if applicable. */
    private fun markWordStartIfNeeded() {
        if (currentWord.isEmpty() && lettersPanel.getShiftState() == ShiftState.SHIFT_ONCE) {
            wordStartCapitalized = true
        }
    }

    // ---- KeyboardActionListener -----------------------------------------

    override fun onCharKey(ch: Char) {
        val ic = currentInputConnection ?: return
        if (activeRootFamily != null) {
            // Space is already in place from the root tap - just close the
            // swap window and let this letter start a fresh word normally.
            activeRootFamily = null
            wordStartCapitalized = false
        }
        if (ch.isLetter()) {
            markWordStartIfNeeded()
            val output = if (lettersPanel.isShiftActive()) ch.uppercaseChar() else ch
            ic.commitText(output.toString(), 1)
            currentWord.append(ch.lowercaseChar())
            statsStore.recordCharactersTyped(1)
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        } else {
            // Only true attaching punctuation pulls a trailing space back
            // ("word." not "word ."). Digits and other symbols (from the
            // ?123 panel) are left alone - deleting a space before "5" or
            // "@" would usually be wrong.
            if (ch in ATTACHING_PUNCTUATION && ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
                ic.deleteSurroundingText(1, 0)
            }
            ic.commitText(ch.toString(), 1)
            currentWord.clear()
            wordStartCapitalized = false
            statsStore.recordCharactersTyped(1)
            refreshPredictions()
        }
    }

    /**
     * A top-row key was tapped. Every root tap now commits + a trailing
     * space IMMEDIATELY - the word is always "finished" in one tap. If the
     * root has a family, the row then shows its OTHER forms as a brief
     * swap window: tapping one of those deletes what was just committed
     * and replaces it. Any other input (a letter, space, backspace,
     * punctuation) just closes that window - the space is already there
     * either way, so nothing else needs to happen.
     */
    override fun onWordSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()

        if (activeRootFamily != null) {
            // Swap-window tap: undo the previous auto-completion, replace it.
            // No manually-typed prefix was involved for this word (it was
            // empty when the swap window opened), so the whole word counts
            // as saved. For the *typed* count, only the net change to the
            // field matters - overwriteLength was already counted when the
            // original word was committed, so don't re-add the whole new
            // word on top of that (that double-counts every swap).
            ic.deleteSurroundingText(overwriteLength, 0)
            ic.commitText(WordCasing.apply(word, effectiveShiftStateForCompletion()), 1)
            ic.commitText(" ", 1)
            currentWord.clear()
            wordStartCapitalized = false
            activeRootFamily = null
            lettersPanel.consumeShiftOnce()
            statsStore.recordCharactersSaved(word.length)
            statsStore.recordCharactersTyped(maxOf(0, (word.length + 1) - overwriteLength))
            refreshPredictions()
            return
        }

        markWordStartIfNeeded()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        val cased = WordCasing.apply(word, effectiveShiftStateForCompletion())
        ic.commitText(cased, 1)
        ic.commitText(" ", 1)
        currentWord.clear()
        // Only the net-new characters beyond what was already typed (and
        // already counted via onCharKey) should count here - the prefix
        // portion was counted once already as it was typed letter by letter.
        val netNew = maxOf(0, word.length - prefix.length)
        statsStore.recordCharactersSaved(netNew)
        statsStore.recordCharactersTyped(netNew + 1)
        statsStore.recordWordCompleted()

        if (engine.hasFamilyVariants(word)) {
            activeRootFamily = word.lowercase()
            overwriteLength = cased.length + 1 // the word plus the space just committed
            // wordStartCapitalized deliberately left as-is here, in case the
            // user swaps to a different form that needs the same casing.
        } else {
            wordStartCapitalized = false
        }
        lettersPanel.consumeShiftOnce()
        refreshPredictions()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (activeRootFamily != null) {
            activeRootFamily = null
            wordStartCapitalized = false
        }
        ic.deleteSurroundingText(1, 0)
        // Re-read the actual word-in-progress from the text field rather
        // than just trimming the tracked currentWord. This matters when
        // backspacing into text that wasn't typed through this tracked
        // flow at all - e.g. the cursor was tapped into the middle of an
        // existing sentence - where currentWord wouldn't reflect what's
        // actually there.
        resyncCurrentWordFromField(ic)
        statsStore.recordBackspace()
        refreshPredictions()
    }

    /**
     * Rebuilds [currentWord] from whatever's actually in the text field
     * immediately before the cursor, scanning back to the nearest word
     * boundary (anything that isn't a letter or apostrophe). This is what
     * lets backspacing into an existing word - not just one typed this
     * session - still show correct completions for it.
     */
    private fun resyncCurrentWordFromField(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'')) {
            start--
        }
        currentWord.clear()
        currentWord.append(before.substring(start).lowercase())
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        if (activeRootFamily != null) {
            // A space is already sitting there from the root tap - just
            // close the window instead of adding a second one.
            activeRootFamily = null
            wordStartCapitalized = false
            refreshPredictions()
            return
        }
        ic.commitText(" ", 1)
        currentWord.clear()
        wordStartCapitalized = false
        statsStore.recordCharactersTyped(1) // the space itself
        statsStore.recordWordCompleted()
        refreshPredictions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        ic.commitText("\n", 1)
        currentWord.clear()
        wordStartCapitalized = false
        statsStore.recordCharactersTyped(1)
        refreshPredictions()
    }

    override fun onShiftToggled() {
        // Visual state is already flipped inside KeyboardPanelView; nothing
        // else to do. Deliberately NOT calling refreshPredictions() here -
        // doing so would let maybeAutoCapitalize() immediately re-arm
        // SHIFT_ONCE right after the user manually cancels it.
    }

    // ---- Macro panel ------------------------------------------------------

    override fun onMacroTapped(slot: Int) {
        val macro = macroStore.getMacro(currentMacroPage, slot)
        if (macro == null) {
            Toast.makeText(this, "Empty - long-press to set up", Toast.LENGTH_SHORT).show()
            return
        }
        statsStore.recordMacroUsed()
        when (macro.type) {
            MacroType.TEXT -> {
                val text = macro.content ?: ""
                currentInputConnection?.commitText(text, 1)
                statsStore.recordCharactersSaved(text.length)
                statsStore.recordCharactersTyped(text.length)
            }
            MacroType.QR -> {
                val file = writeQrToFile(macro.content ?: "")
                if (file != null) {
                    insertImageFile(file)
                } else {
                    Toast.makeText(this, "Couldn't generate that QR code", Toast.LENGTH_SHORT).show()
                }
            }
            MacroType.IMAGE -> macro.imagePath?.let { insertImageFile(File(it)) }
        }
    }

    override fun onMacroLongPressed(slot: Int) {
        val intent = Intent(this, MacroEditActivity::class.java).apply {
            putExtra(MacroEditActivity.EXTRA_PAGE, currentMacroPage)
            putExtra(MacroEditActivity.EXTRA_SLOT, slot)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun writeQrToFile(content: String): File? {
        val bitmap = QrCodeGenerator.generate(content) ?: return null
        return try {
            val dir = File(filesDir, "macro_images").apply { mkdirs() }
            val file = File(dir, "qr_${System.currentTimeMillis()}.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Inserts [file] (a PNG) into the current field if it declares support
     * for image content (checked via EditorInfo.contentMimeTypes, the
     * Android API for exactly this) - otherwise copies it to the clipboard
     * so the user can paste it manually, since most ordinary text fields
     * don't accept images from a keyboard at all. Either way the user is
     * told which happened.
     */
    private fun insertImageFile(file: File) {
        val ic = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        if (ic != null && editorInfo != null) {
            val supportedTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            val canCommit = supportedTypes.any { it.startsWith("image/") }
            if (canCommit) {
                grantUriPermission(
                    editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val contentInfo = InputContentInfoCompat(
                    uri, ClipDescription("macro image", arrayOf("image/png")), null
                )
                val success = InputConnectionCompat.commitContent(
                    ic, editorInfo, contentInfo,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                )
                if (success) {
                    Toast.makeText(this, "Image inserted", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Fallback: this field doesn't accept images directly.
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "macro image", uri))
        Toast.makeText(
            this, "This field can't accept images directly - copied to clipboard, paste it manually",
            Toast.LENGTH_LONG
        ).show()
    }
}
