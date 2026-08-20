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
        private val ATTACHING_PUNCTUATION = setOf('.', ',', '!', '?', ':', ';')
    }

    private lateinit var container: FrameLayout
    private lateinit var lettersPanel: KeyboardPanelView
    private lateinit var symbolsPanel: SymbolKeyboardView
    private lateinit var macroPanel: MacroKeyboardView
    private lateinit var macroStore: MacroStore

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

    override fun onCreate() {
        super.onCreate()
        if (!engine.isLoaded()) {
            assets.open("wordlist.txt").use { engine.load(it) }
        }
        macroStore = MacroStore(this)
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
        // Selection stays frequency-based; this only reorders the selected
        // words for display, shortest first, so they're easier to scan.
        val completions = if (family != null) {
            // Only the variations, not the root itself - you already picked
            // it by tapping it, so re-showing it would just take an extra
            // tap to finish with no ending.
            engine.familyMembers(family).filterNot { it == family }
        } else {
            engine.topCompletions(prefix)
        }.sortedBy { it.length }
        lettersPanel.updateWordCompletions(completions, rtl)
        lettersPanel.setSoleCompletion(engine.soleCompletion(prefix).takeIf { prefix.isNotEmpty() })
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
        val wasInFamilyMode = activeRootFamily != null
        activeRootFamily = null // typing always exits family mode
        if (ch.isLetter()) {
            if (wasInFamilyMode) {
                // The previous root word is considered "finished" - a space
                // goes in before starting this new word, so you never have
                // to manually hit space after browsing a family's endings.
                ic.commitText(" ", 1)
                currentWord.clear()
                wordStartCapitalized = false
            }
            markWordStartIfNeeded()
            val output = if (lettersPanel.isShiftActive()) ch.uppercaseChar() else ch
            ic.commitText(output.toString(), 1)
            currentWord.append(ch.lowercaseChar())
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
            refreshPredictions()
        }
    }

    /**
     * A top-row key was tapped. Behavior depends on whether we're at the
     * root level or already inside a family:
     *  - Root level, root HAS variants: commit it WITHOUT a trailing space
     *    and switch the row to show just those variants (not the root
     *    itself again - see refreshPredictions).
     *  - Root level, root has NO variants: nothing useful to show in a
     *    family row, so finish immediately instead of a dead-end tap.
     *  - Inside a family: [word] is the final choice. Commit it plus a
     *    trailing space and return to root-level suggestions.
     * Deletes whatever's already typed of the word first and re-commits
     * the whole thing correctly cased, so proper-noun/shift capitalization
     * is right even if the typed prefix was lowercase.
     */
    override fun onWordSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()

        if (activeRootFamily == null && engine.hasFamilyVariants(word)) {
            markWordStartIfNeeded()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(word, effectiveShiftStateForCompletion()), 1)
            currentWord.clear()
            currentWord.append(word.lowercase())
            activeRootFamily = word.lowercase()
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        } else {
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(word, effectiveShiftStateForCompletion()), 1)
            ic.commitText(" ", 1)
            currentWord.clear()
            wordStartCapitalized = false
            activeRootFamily = null
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        }
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        refreshPredictions()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        val sole = lettersPanel.getSoleCompletion()
        if (sole != null) {
            val prefix = currentWord.toString()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(sole, effectiveShiftStateForCompletion()), 1)
            lettersPanel.consumeShiftOnce()
        }
        ic.commitText(" ", 1)
        currentWord.clear()
        wordStartCapitalized = false
        refreshPredictions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        ic.commitText("\n", 1)
        currentWord.clear()
        wordStartCapitalized = false
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
        when (macro.type) {
            MacroType.TEXT -> currentInputConnection?.commitText(macro.content ?: "", 1)
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

