package com.predictivekb.ime

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MacroEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val EXTRA_SLOT = "slot"
    }

    private lateinit var store: MacroStore
    private var page = 0
    private var slot = 0

    private lateinit var labelInput: EditText
    private lateinit var typeGroup: RadioGroup
    private lateinit var contentLabel: TextView
    private lateinit var contentInput: EditText
    private lateinit var imageSection: android.widget.LinearLayout
    private lateinit var imagePreview: ImageView
    private lateinit var deleteButton: Button

    /** Path to the image for this macro - either already-saved, or newly picked this session. */
    private var pendingImagePath: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyPickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_edit)
        store = MacroStore(this)

        page = intent.getIntExtra(EXTRA_PAGE, 0)
        slot = intent.getIntExtra(EXTRA_SLOT, 0)

        labelInput = findViewById(R.id.label_input)
        typeGroup = findViewById(R.id.type_group)
        contentLabel = findViewById(R.id.content_label)
        contentInput = findViewById(R.id.content_input)
        imageSection = findViewById(R.id.image_section)
        imagePreview = findViewById(R.id.image_preview)
        deleteButton = findViewById(R.id.delete_button)

        typeGroup.setOnCheckedChangeListener { _, _ -> updateVisibilityForType() }
        findViewById<Button>(R.id.choose_image_button).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
        deleteButton.setOnClickListener { delete() }

        loadExisting()
        updateVisibilityForType()
    }

    private fun loadExisting() {
        val macro = store.getMacro(page, slot)
        if (macro != null) {
            labelInput.setText(macro.label)
            when (macro.type) {
                MacroType.TEXT -> typeGroup.check(R.id.type_text)
                MacroType.QR -> typeGroup.check(R.id.type_qr)
                MacroType.IMAGE -> typeGroup.check(R.id.type_image)
            }
            contentInput.setText(macro.content ?: "")
            pendingImagePath = macro.imagePath
            if (pendingImagePath != null) {
                imagePreview.setImageURI(Uri.fromFile(File(pendingImagePath!!)))
            }
            deleteButton.visibility = android.view.View.VISIBLE
        } else {
            typeGroup.check(R.id.type_text)
            deleteButton.visibility = android.view.View.GONE
        }
    }

    private fun updateVisibilityForType() {
        val isImage = typeGroup.checkedRadioButtonId == R.id.type_image
        val isQr = typeGroup.checkedRadioButtonId == R.id.type_qr
        imageSection.visibility = if (isImage) android.view.View.VISIBLE else android.view.View.GONE
        contentInput.visibility = if (isImage) android.view.View.GONE else android.view.View.VISIBLE
        contentLabel.visibility = if (isImage) android.view.View.GONE else android.view.View.VISIBLE
        contentLabel.text = if (isQr) "URL or text to encode as a QR code" else "Text to insert"
    }

    private fun copyPickedImage(sourceUri: Uri) {
        try {
            val dir = File(filesDir, "macro_images").apply { mkdirs() }
            val destFile = File(dir, "macro_${page}_${slot}_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            pendingImagePath = destFile.absolutePath
            imagePreview.setImageURI(Uri.fromFile(destFile))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val label = labelInput.text.toString().trim()
        if (label.isEmpty()) {
            Toast.makeText(this, "Give the button a label first", Toast.LENGTH_SHORT).show()
            return
        }
        val type = when (typeGroup.checkedRadioButtonId) {
            R.id.type_qr -> MacroType.QR
            R.id.type_image -> MacroType.IMAGE
            else -> MacroType.TEXT
        }
        if (type == MacroType.IMAGE && pendingImagePath == null) {
            Toast.makeText(this, "Choose an image first", Toast.LENGTH_SHORT).show()
            return
        }
        val content = contentInput.text.toString()
        if (type != MacroType.IMAGE && content.isBlank()) {
            Toast.makeText(this, "Enter some content first", Toast.LENGTH_SHORT).show()
            return
        }

        store.saveMacro(
            Macro(
                page = page,
                slot = slot,
                label = label,
                type = type,
                content = if (type == MacroType.IMAGE) null else content,
                imagePath = if (type == MacroType.IMAGE) pendingImagePath else null
            )
        )
        finish()
    }

    private fun delete() {
        store.deleteMacro(page, slot)
        finish()
    }
}
