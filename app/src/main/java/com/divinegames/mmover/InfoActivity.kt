package com.divinegames.mmover

import android.os.Bundle
import android.widget.Button
import androidx.core.text.HtmlCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.divinegames.mmover.databinding.ActivityInfoBinding
import com.divinegames.mmover.databinding.ItemFaqBinding

class InfoActivity : BaseActivity() {
    private lateinit var binding: ActivityInfoBinding
    private val expandedQuestions = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyHelpInsets(binding.root)

        val titleResId = intent.getIntExtra("EXTRA_TITLE_RES_ID", R.string.menu_faq)
        if (titleResId != 0) title = getString(titleResId)
        binding.infoToolbar.title = title
        binding.infoToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val textResId = intent.getIntExtra("EXTRA_TEXT_RES_ID", 0)
        val isFaq = textResId == R.string.faq_text
        binding.faqIntro.isVisible = isFaq
        binding.faqList.isVisible = isFaq
        binding.versionText.isVisible = isFaq
        if (isFaq) {
            binding.versionText.text = getString(R.string.help_version, BuildConfig.VERSION_NAME)
            expandedQuestions.addAll(savedInstanceState?.getIntArray("expanded_questions")?.toList().orEmpty())
            showQuestions()
        } else {
            val imageResId = intent.getIntExtra("EXTRA_IMAGE_RES_ID", 0)
            binding.infoImageView.isVisible = imageResId != 0
            if (imageResId != 0) binding.infoImageView.setImageResource(imageResId)
            binding.infoTextView.isVisible = textResId != 0
            if (textResId != 0) binding.infoTextView.text =
                HtmlCompat.fromHtml(getString(textResId), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun showQuestions() {
        val questions = resources.getStringArray(R.array.faq_questions)
        val answers = resources.getStringArray(R.array.faq_answers)
        questions.forEachIndexed { index, question ->
            val row = ItemFaqBinding.inflate(layoutInflater, binding.faqList, false)
            row.questionText.text = question
            row.questionButton.contentDescription = question
            ViewCompat.setAccessibilityDelegate(row.questionButton, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: android.view.View, info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            })
            row.answerText.text = HtmlCompat.fromHtml(answers[index], HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
            fun render() {
                val expanded = index in expandedQuestions
                row.answerText.isVisible = expanded
                row.expandIcon.rotation = if (expanded) 180f else 0f
                ViewCompat.setStateDescription(row.questionButton,
                    getString(if (expanded) R.string.help_expanded else R.string.help_collapsed))
            }
            row.questionButton.setOnClickListener {
                if (!expandedQuestions.add(index)) expandedQuestions.remove(index)
                render()
            }
            // Repeated row IDs must not share hierarchy state; expansion is saved explicitly.
            row.root.isSaveFromParentEnabled = false
            render()
            binding.faqList.addView(row.root)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray("expanded_questions", expandedQuestions.toIntArray())
        super.onSaveInstanceState(outState)
    }
}
