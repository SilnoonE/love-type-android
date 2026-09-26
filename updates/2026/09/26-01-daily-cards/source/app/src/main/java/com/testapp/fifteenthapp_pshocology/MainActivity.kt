package com.testapp.fifteenthapp_pshocology

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    enum class ScreenState {
        SPLASH, HOME, INTRO, TEST_QUESTION, TEST_LOADING, TEST_RESULT,
        DAILY_QUESTION, DAILY_RESULT, DAILY_COLLECTION
    }

    private var currentScreen: ScreenState = ScreenState.SPLASH
    private var screenOrigin: ScreenState = ScreenState.HOME

    // Existing test state
    private var currentQuestionIndex = 0
    private val scores = mutableMapOf(
        ScoringType.EMOTIONAL to 0,
        ScoringType.CLINGY to 0,
        ScoringType.LEADING to 0,
        ScoringType.FREE to 0
    )
    private var lastResult: ResultType? = null

    // Daily Feature Repository & Scheduler
    private lateinit var repository: DailyCardRepository
    private lateinit var scheduler: DailyQuestionScheduler

    // Daily Feature State
    private var currentDailyDateKey: String = ""
    private var currentDailyQuestion: DailyQuestion? = null
    private var selectedOptionId: String? = null
    private var currentDailyRecord: DailyCardRecord? = null
    private var isFilterFavoritesOnly: Boolean = false

    // Views
    private lateinit var layoutSplash: View
    private lateinit var layoutHome: View
    private lateinit var layoutIntro: View
    private lateinit var layoutQuestion: View
    private lateinit var layoutLoading: View
    private lateinit var layoutResult: View
    private lateinit var layoutDailyQuestion: View
    private lateinit var layoutDailyResult: View
    private lateinit var layoutDailyCollection: View

    private lateinit var tvLoadingMsg: TextView
    private lateinit var adView: AdView
    private var mInterstitialAd: InterstitialAd? = null

    // RecyclerView for Collection
    private lateinit var collectionAdapter: DailyCardAdapter
    private lateinit var rvDailyCollection: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = DailyCardRepository(this)
        scheduler = DailyQuestionScheduler(repository)

        // AdMob initialization
        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        loadInterstitialAd()

        initViews()
        setupBackPressedHandler()

        if (savedInstanceState != null) {
            val savedScreenName = savedInstanceState.getString("KEY_CURRENT_SCREEN", ScreenState.HOME.name)
            currentScreen = try { ScreenState.valueOf(savedScreenName) } catch (e: Exception) { ScreenState.HOME }
            currentDailyDateKey = savedInstanceState.getString("KEY_DAILY_DATE", scheduler.getTodayDateKey())
            selectedOptionId = savedInstanceState.getString("KEY_SELECTED_OPTION", null)
            isFilterFavoritesOnly = savedInstanceState.getBoolean("KEY_FILTER_FAV", false)

            if (currentScreen == ScreenState.SPLASH) {
                showSplashScreen()
            } else {
                restoreScreen(currentScreen)
            }
        } else {
            showSplashScreen()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("KEY_CURRENT_SCREEN", currentScreen.name)
        outState.putString("KEY_DAILY_DATE", currentDailyDateKey)
        outState.putString("KEY_SELECTED_OPTION", selectedOptionId)
        outState.putBoolean("KEY_FILTER_FAV", isFilterFavoritesOnly)
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    ScreenState.DAILY_QUESTION -> {
                        if (screenOrigin == ScreenState.DAILY_RESULT && currentDailyRecord != null) {
                            showDailyResultScreen(currentDailyRecord!!, fromCollection = (screenOrigin == ScreenState.DAILY_COLLECTION))
                        } else {
                            showHomeScreen()
                        }
                    }
                    ScreenState.DAILY_RESULT -> {
                        if (screenOrigin == ScreenState.DAILY_COLLECTION) {
                            showDailyCollectionScreen()
                        } else {
                            showHomeScreen()
                        }
                    }
                    ScreenState.DAILY_COLLECTION -> showHomeScreen()
                    ScreenState.INTRO -> showHomeScreen()
                    ScreenState.TEST_QUESTION -> showHomeScreen()
                    ScreenState.TEST_RESULT -> showHomeScreen()
                    ScreenState.TEST_LOADING -> showHomeScreen()
                    ScreenState.HOME -> finish()
                    ScreenState.SPLASH -> finish()
                }
            }
        })
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                }
            })
    }

    private fun showInterstitialAdWithAction(action: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadInterstitialAd()
                    action()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    mInterstitialAd = null
                    action()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            action()
        }
    }

    private fun initViews() {
        layoutSplash = findViewById(R.id.layout_splash)
        layoutHome = findViewById(R.id.layout_home)
        layoutIntro = findViewById(R.id.layout_intro)
        layoutQuestion = findViewById(R.id.layout_question)
        layoutLoading = findViewById(R.id.layout_loading)
        layoutResult = findViewById(R.id.layout_result)
        layoutDailyQuestion = findViewById(R.id.layout_daily_question)
        layoutDailyResult = findViewById(R.id.layout_daily_result)
        layoutDailyCollection = findViewById(R.id.layout_daily_collection)
        tvLoadingMsg = findViewById(R.id.tv_loading_msg)

        // Home Buttons
        findViewById<Button>(R.id.btn_home_today_answer).setOnClickListener {
            screenOrigin = ScreenState.HOME
            showDailyQuestionScreen(scheduler.getTodayDateKey())
        }
        findViewById<Button>(R.id.btn_home_today_view).setOnClickListener {
            val todayRecord = repository.getTodayRecord(scheduler.getTodayDateKey())
            if (todayRecord != null) {
                screenOrigin = ScreenState.HOME
                showDailyResultScreen(todayRecord)
            } else {
                screenOrigin = ScreenState.HOME
                showDailyQuestionScreen(scheduler.getTodayDateKey())
            }
        }
        findViewById<Button>(R.id.btn_home_start_test).setOnClickListener {
            showIntroScreen()
        }
        findViewById<TextView>(R.id.btn_home_collection_all).setOnClickListener {
            showDailyCollectionScreen()
        }
        findViewById<Button>(R.id.btn_home_first_card).setOnClickListener {
            screenOrigin = ScreenState.HOME
            showDailyQuestionScreen(scheduler.getTodayDateKey())
        }

        // Intro Screen
        findViewById<ImageButton>(R.id.btn_intro_back).setOnClickListener {
            showHomeScreen()
        }
        findViewById<Button>(R.id.btn_start_test).setOnClickListener {
            startTest()
        }

        // Test Result Screen
        findViewById<ImageButton>(R.id.btn_result_back).setOnClickListener {
            showHomeScreen()
        }
        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            showInterstitialAdWithAction { showHomeScreen() }
        }
        findViewById<Button>(R.id.btn_save_image).setOnClickListener {
            showInterstitialAdWithAction { processCardAction(isSave = true) }
        }
        findViewById<Button>(R.id.btn_share_image).setOnClickListener {
            showInterstitialAdWithAction { processCardAction(isSave = false) }
        }

        // Daily Question Screen Buttons
        findViewById<ImageButton>(R.id.btn_daily_q_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<Button>(R.id.btn_daily_q_submit).setOnClickListener {
            submitDailyAnswer()
        }

        // Daily Result Screen Buttons
        findViewById<ImageButton>(R.id.btn_daily_r_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<Button>(R.id.btn_daily_r_favorite).setOnClickListener {
            toggleCurrentRecordFavorite()
        }
        findViewById<Button>(R.id.btn_daily_r_share).setOnClickListener {
            val rec = currentDailyRecord
            if (rec != null) {
                DailyCardShareRenderer.shareRecord(this, rec)
            }
        }
        findViewById<Button>(R.id.btn_daily_r_collection).setOnClickListener {
            showDailyCollectionScreen()
        }
        findViewById<TextView>(R.id.btn_daily_r_edit_today).setOnClickListener {
            val todayKey = scheduler.getTodayDateKey()
            if (currentDailyRecord?.dateKey == todayKey) {
                screenOrigin = ScreenState.DAILY_RESULT
                showDailyQuestionScreen(todayKey, isEditing = true)
            }
        }

        // Daily Collection Screen Buttons
        findViewById<ImageButton>(R.id.btn_daily_c_back).setOnClickListener {
            showHomeScreen()
        }
        findViewById<Button>(R.id.btn_filter_all).setOnClickListener {
            isFilterFavoritesOnly = false
            updateCollectionFilterButtons()
            updateCollectionList()
        }
        findViewById<Button>(R.id.btn_filter_favorites).setOnClickListener {
            isFilterFavoritesOnly = true
            updateCollectionFilterButtons()
            updateCollectionList()
        }
        findViewById<Button>(R.id.btn_collection_empty_today).setOnClickListener {
            screenOrigin = ScreenState.DAILY_COLLECTION
            showDailyQuestionScreen(scheduler.getTodayDateKey())
        }

        // RecyclerView Collection setup
        rvDailyCollection = findViewById(R.id.rv_daily_collection)
        rvDailyCollection.layoutManager = LinearLayoutManager(this)
        collectionAdapter = DailyCardAdapter(
            onItemClick = { record ->
                screenOrigin = ScreenState.DAILY_COLLECTION
                showDailyResultScreen(record, fromCollection = true)
            },
            onFavoriteToggle = { record ->
                repository.setFavorite(record.dateKey, !record.isFavorite) { success ->
                    if (success) {
                        runOnUiThread {
                            updateCollectionList()
                            updateHomeScreen()
                        }
                    }
                }
            }
        )
        rvDailyCollection.adapter = collectionAdapter
    }

    private fun hideAll() {
        layoutSplash.visibility = View.GONE
        layoutHome.visibility = View.GONE
        layoutIntro.visibility = View.GONE
        layoutQuestion.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.GONE
        layoutDailyQuestion.visibility = View.GONE
        layoutDailyResult.visibility = View.GONE
        layoutDailyCollection.visibility = View.GONE
    }

    private fun restoreScreen(screen: ScreenState) {
        when (screen) {
            ScreenState.HOME -> showHomeScreen()
            ScreenState.INTRO -> showIntroScreen()
            ScreenState.TEST_QUESTION -> showQuestionScreen()
            ScreenState.TEST_RESULT -> {
                if (lastResult != null) showResultScreen() else showHomeScreen()
            }
            ScreenState.DAILY_QUESTION -> showDailyQuestionScreen(currentDailyDateKey)
            ScreenState.DAILY_RESULT -> {
                val rec = currentDailyRecord ?: repository.getTodayRecord(currentDailyDateKey)
                if (rec != null) showDailyResultScreen(rec) else showHomeScreen()
            }
            ScreenState.DAILY_COLLECTION -> showDailyCollectionScreen()
            else -> showHomeScreen()
        }
    }

    // --- HOME SCREEN ---

    private fun showSplashScreen() {
        currentScreen = ScreenState.SPLASH
        hideAll()
        layoutSplash.visibility = View.VISIBLE
        layoutSplash.postDelayed({
            showHomeScreen()
        }, 1500)
    }

    private fun showHomeScreen() {
        currentScreen = ScreenState.HOME
        hideAll()
        layoutHome.visibility = View.VISIBLE
        updateHomeScreen()
    }

    private fun updateHomeScreen() {
        val todayDateKey = scheduler.getTodayDateKey()
        val question = scheduler.getQuestionForDate(todayDateKey)
        val todayRecord = repository.getTodayRecord(todayDateKey)

        // Format Date string: e.g. "9월 26일"
        val formattedDate = formatKoreanDate(todayDateKey)

        findViewById<TextView>(R.id.tv_home_today_date).text = formattedDate
        findViewById<TextView>(R.id.tv_home_today_chip).text = question.category
        findViewById<TextView>(R.id.tv_home_today_prompt).text = question.prompt

        val layoutAnswered = findViewById<LinearLayout>(R.id.layout_home_today_answered)
        val tvSubtext = findViewById<TextView>(R.id.tv_home_today_subtext)
        val btnAnswer = findViewById<Button>(R.id.btn_home_today_answer)
        val btnView = findViewById<Button>(R.id.btn_home_today_view)
        val tvResultTitle = findViewById<TextView>(R.id.tv_home_today_result_title)

        if (todayRecord != null) {
            layoutAnswered.visibility = View.VISIBLE
            tvSubtext.visibility = View.GONE
            btnAnswer.visibility = View.GONE
            btnView.visibility = View.VISIBLE
            tvResultTitle.text = todayRecord.selectedOption?.resultTitle ?: ""
        } else {
            layoutAnswered.visibility = View.GONE
            tvSubtext.visibility = View.VISIBLE
            btnAnswer.visibility = View.VISIBLE
            btnView.visibility = View.GONE
        }

        // Section 3: Collection Preview (Up to 3 items)
        val allRecords = repository.getAllRecords()
        findViewById<TextView>(R.id.tv_home_collection_count).text = "지금까지 ${allRecords.size}장의 카드"

        val container = findViewById<LinearLayout>(R.id.ll_home_recent_cards)
        val emptyView = findViewById<LinearLayout>(R.id.layout_home_empty_cards)

        container.removeAllViews()

        if (allRecords.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            container.visibility = View.VISIBLE

            val recent3 = allRecords.take(3)
            val inflater = LayoutInflater.from(this)
            recent3.forEach { record ->
                val itemView = inflater.inflate(R.layout.item_daily_card, container, false)
                itemView.findViewById<TextView>(R.id.tv_item_chip).text = record.questionSnapshot.category
                itemView.findViewById<TextView>(R.id.tv_item_date).text = formatKoreanDate(record.dateKey)

                val illuResId = DailyCardShareRenderer.getIllustrationResId(record.questionSnapshot.illustrationKey)
                itemView.findViewById<ImageView>(R.id.iv_item_illustration).setImageResource(illuResId)

                val opt = record.selectedOption
                itemView.findViewById<TextView>(R.id.tv_item_result_title).text = opt?.resultTitle ?: ""
                itemView.findViewById<TextView>(R.id.tv_item_selected_option).text = "답변: ${opt?.text ?: ""}"

                val favBtn = itemView.findViewById<ImageButton>(R.id.btn_item_favorite)
                if (record.isFavorite) {
                    favBtn.setImageResource(R.drawable.ic_favorite_filled)
                    favBtn.contentDescription = "즐겨찾기 해제"
                } else {
                    favBtn.setImageResource(R.drawable.ic_favorite_border)
                    favBtn.contentDescription = "즐겨찾기 추가"
                }

                favBtn.setOnClickListener {
                    repository.setFavorite(record.dateKey, !record.isFavorite) { success ->
                        if (success) runOnUiThread { updateHomeScreen() }
                    }
                }

                itemView.setOnClickListener {
                    screenOrigin = ScreenState.HOME
                    showDailyResultScreen(record)
                }

                container.addView(itemView)
            }
        }
    }

    private fun formatKoreanDate(dateKey: String): String {
        return try {
            val date = LocalDate.parse(dateKey, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            date.format(DateTimeFormatter.ofPattern("M월 d일"))
        } catch (e: Exception) {
            dateKey
        }
    }

    // --- DAILY QUESTION SCREEN ---

    private fun showDailyQuestionScreen(dateKey: String, isEditing: Boolean = false) {
        currentScreen = ScreenState.DAILY_QUESTION
        currentDailyDateKey = dateKey
        val question = scheduler.getQuestionForDate(dateKey)
        currentDailyQuestion = question

        hideAll()
        layoutDailyQuestion.visibility = View.VISIBLE

        val existingRecord = repository.getRecordByDate(dateKey)
        selectedOptionId = if (isEditing && existingRecord != null) {
            existingRecord.selectedOptionId
        } else {
            null
        }

        findViewById<TextView>(R.id.tv_daily_q_date).text = formatKoreanDate(dateKey)
        findViewById<TextView>(R.id.tv_daily_q_chip).text = question.category

        val illuResId = DailyCardShareRenderer.getIllustrationResId(question.illustrationKey)
        findViewById<ImageView>(R.id.iv_daily_q_illustration).setImageResource(illuResId)

        findViewById<TextView>(R.id.tv_daily_q_prompt).text = question.prompt

        val btnSubmit = findViewById<Button>(R.id.btn_daily_q_submit)
        btnSubmit.text = if (isEditing) "카드 업데이트" else "내 카드 만들기"
        btnSubmit.isEnabled = (selectedOptionId != null)

        val optionsContainer = findViewById<LinearLayout>(R.id.ll_daily_q_options)
        optionsContainer.removeAllViews()

        val inflater = LayoutInflater.from(this)
        question.options.forEach { option ->
            val optView = inflater.inflate(R.layout.item_daily_option, optionsContainer, false)
            val tvId = optView.findViewById<TextView>(R.id.tv_option_id)
            val tvText = optView.findViewById<TextView>(R.id.tv_option_text)
            val ivCheck = optView.findViewById<ImageView>(R.id.iv_option_check)

            tvId.text = option.id
            tvText.text = option.text

            val isSelected = (option.id == selectedOptionId)
            optView.setBackgroundResource(if (isSelected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

            optView.setOnClickListener {
                selectedOptionId = option.id
                btnSubmit.isEnabled = true
                updateOptionSelections(optionsContainer, question.options, selectedOptionId)
            }

            optionsContainer.addView(optView)
        }
    }

    private fun updateOptionSelections(container: LinearLayout, options: List<DailyOption>, selectedId: String?) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val option = options.getOrNull(i) ?: continue
            val ivCheck = child.findViewById<ImageView>(R.id.iv_option_check)
            val isSelected = (option.id == selectedId)

            child.setBackgroundResource(if (isSelected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    private fun submitDailyAnswer() {
        val dateKey = currentDailyDateKey
        val question = currentDailyQuestion ?: return
        val optionId = selectedOptionId ?: return

        val btnSubmit = findViewById<Button>(R.id.btn_daily_q_submit)
        btnSubmit.isEnabled = false

        // Date check: ensure local date has not shifted
        val actualToday = scheduler.getTodayDateKey()
        if (dateKey != actualToday && currentDailyRecord == null) {
            Toast.makeText(this, "날짜가 바뀌어 새로운 질문을 준비했어요.", Toast.LENGTH_SHORT).show()
            showDailyQuestionScreen(actualToday)
            return
        }

        repository.upsertAnswer(dateKey, question, optionId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "오늘의 카드가 모음집에 저장됐어요.", Toast.LENGTH_SHORT).show()
                    val savedRecord = repository.getRecordByDate(dateKey)
                    if (savedRecord != null) {
                        showDailyResultScreen(savedRecord)
                    } else {
                        showHomeScreen()
                    }
                } else {
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "저장하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- DAILY RESULT SCREEN ---

    private fun showDailyResultScreen(record: DailyCardRecord, fromCollection: Boolean = false) {
        currentScreen = ScreenState.DAILY_RESULT
        currentDailyRecord = record
        currentDailyDateKey = record.dateKey

        if (fromCollection) {
            screenOrigin = ScreenState.DAILY_COLLECTION
        }

        hideAll()
        layoutDailyResult.visibility = View.VISIBLE

        val option = record.selectedOption ?: return

        findViewById<TextView>(R.id.tv_daily_r_date).text = formatKoreanDate(record.dateKey)
        findViewById<TextView>(R.id.tv_daily_r_chip).text = record.questionSnapshot.category

        val illuResId = DailyCardShareRenderer.getIllustrationResId(record.questionSnapshot.illustrationKey)
        findViewById<ImageView>(R.id.iv_daily_r_illustration).setImageResource(illuResId)

        findViewById<TextView>(R.id.tv_daily_r_result_title).text = option.resultTitle
        findViewById<TextView>(R.id.tv_daily_r_interpretation).text = option.interpretation
        findViewById<TextView>(R.id.tv_daily_r_conversation).text = "“${option.conversation}”"

        findViewById<TextView>(R.id.tv_daily_r_question_prompt).text = record.questionSnapshot.prompt
        findViewById<TextView>(R.id.tv_daily_r_selected_option).text = option.text

        // Favorite Button UI
        updateFavoriteButtonUI(record.isFavorite)

        // Show Edit button ONLY if record is for today
        val btnEditToday = findViewById<TextView>(R.id.btn_daily_r_edit_today)
        val todayKey = scheduler.getTodayDateKey()
        if (record.dateKey == todayKey) {
            btnEditToday.visibility = View.VISIBLE
        } else {
            btnEditToday.visibility = View.GONE
        }
    }

    private fun updateFavoriteButtonUI(isFavorite: Boolean) {
        val btnFav = findViewById<Button>(R.id.btn_daily_r_favorite)
        if (isFavorite) {
            btnFav.text = "즐겨찾기됨"
            btnFav.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_filled, 0, 0, 0)
        } else {
            btnFav.text = "즐겨찾기"
            btnFav.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_border, 0, 0, 0)
        }
    }

    private fun toggleCurrentRecordFavorite() {
        val record = currentDailyRecord ?: return
        val newFav = !record.isFavorite
        repository.setFavorite(record.dateKey, newFav) { success ->
            if (success) {
                runOnUiThread {
                    currentDailyRecord = record.copy(isFavorite = newFav)
                    updateFavoriteButtonUI(newFav)
                    updateHomeScreen()
                    updateCollectionList()
                }
            }
        }
    }

    // --- DAILY COLLECTION SCREEN ---

    private fun showDailyCollectionScreen() {
        currentScreen = ScreenState.DAILY_COLLECTION
        hideAll()
        layoutDailyCollection.visibility = View.VISIBLE

        updateCollectionFilterButtons()
        updateCollectionList()
    }

    private fun updateCollectionFilterButtons() {
        val btnAll = findViewById<Button>(R.id.btn_filter_all)
        val btnFav = findViewById<Button>(R.id.btn_filter_favorites)

        if (!isFilterFavoritesOnly) {
            btnAll.setBackgroundResource(R.drawable.bg_button_main)
            btnAll.setTextColor(resources.getColor(android.R.color.white, theme))
            btnFav.setBackgroundResource(R.drawable.bg_button_secondary)
            btnFav.setTextColor(resources.getColor(R.color.main_pink, theme))
        } else {
            btnAll.setBackgroundResource(R.drawable.bg_button_secondary)
            btnAll.setTextColor(resources.getColor(R.color.main_pink, theme))
            btnFav.setBackgroundResource(R.drawable.bg_button_main)
            btnFav.setTextColor(resources.getColor(android.R.color.white, theme))
        }
    }

    private fun updateCollectionList() {
        val allRecords = repository.getAllRecords()
        val totalCountView = findViewById<TextView>(R.id.tv_daily_c_total_count)

        val emptyAll = findViewById<LinearLayout>(R.id.layout_collection_empty_all)
        val emptyFav = findViewById<LinearLayout>(R.id.layout_collection_empty_fav)

        val displayRecords = if (isFilterFavoritesOnly) {
            allRecords.filter { it.isFavorite }
        } else {
            allRecords
        }

        if (isFilterFavoritesOnly) {
            totalCountView.text = "즐겨찾기 ${displayRecords.size}개"
        } else {
            totalCountView.text = "총 ${allRecords.size}개"
        }

        if (allRecords.isEmpty()) {
            emptyAll.visibility = View.VISIBLE
            emptyFav.visibility = View.GONE
            rvDailyCollection.visibility = View.GONE
        } else if (isFilterFavoritesOnly && displayRecords.isEmpty()) {
            emptyAll.visibility = View.GONE
            emptyFav.visibility = View.VISIBLE
            rvDailyCollection.visibility = View.GONE
        } else {
            emptyAll.visibility = View.GONE
            emptyFav.visibility = View.GONE
            rvDailyCollection.visibility = View.VISIBLE
            collectionAdapter.submitList(displayRecords)
        }
    }

    // --- EXISTING 20-QUESTION TEST LOGIC ---

    private fun showIntroScreen() {
        currentScreen = ScreenState.INTRO
        hideAll()
        layoutIntro.visibility = View.VISIBLE
    }

    private fun startTest() {
        currentQuestionIndex = 0
        scores.keys.forEach { scores[it] = 0 }
        showQuestionScreen()
    }

    private fun showQuestionScreen() {
        currentScreen = ScreenState.TEST_QUESTION
        hideAll()
        layoutQuestion.visibility = View.VISIBLE
        updateQuestion()
    }

    private fun updateQuestion() {
        val questions = AppData.questions
        if (currentQuestionIndex >= questions.size) {
            showLoadingScreen("당신의 연애 성향을\n분석하고 있어요...")
            layoutLoading.postDelayed({ showResultScreen() }, 2000)
            return
        }

        val q = questions[currentQuestionIndex]
        findViewById<TextView>(R.id.tv_progress).text = "${currentQuestionIndex + 1} / ${questions.size}"
        findViewById<ProgressBar>(R.id.pb_test).progress = ((currentQuestionIndex + 1).toFloat() / questions.size * 100).toInt()
        findViewById<TextView>(R.id.tv_question_title).text = q.title
        findViewById<TextView>(R.id.tv_question_category).text = q.category

        val answerContainer = findViewById<LinearLayout>(R.id.ll_answers)
        answerContainer.removeAllViews()

        val shuffledAnswers = q.answers.shuffled()
        shuffledAnswers.forEach { answer ->
            val btn = layoutInflater.inflate(R.layout.item_answer, answerContainer, false) as Button
            btn.text = answer.text
            btn.setOnClickListener {
                scores[answer.type] = (scores[answer.type] ?: 0) + 1
                currentQuestionIndex++

                val fadeOut = AlphaAnimation(1f, 0.5f).apply { duration = 100 }
                btn.startAnimation(fadeOut)
                btn.postDelayed({ updateQuestion() }, 100)
            }
            answerContainer.addView(btn)
        }
    }

    private fun showLoadingScreen(msg: String) {
        currentScreen = ScreenState.TEST_LOADING
        hideAll()
        tvLoadingMsg.text = msg
        layoutLoading.visibility = View.VISIBLE
    }

    private fun showResultScreen() {
        currentScreen = ScreenState.TEST_RESULT
        hideAll()
        layoutResult.visibility = View.VISIBLE
        val finalResult = calculateResult()
        lastResult = finalResult

        findViewById<TextView>(R.id.tv_result_title).text = finalResult.title
        findViewById<TextView>(R.id.tv_result_header).text = finalResult.subtitle

        val resId = resources.getIdentifier(finalResult.id.lowercase(), "drawable", packageName)
        if (resId != 0) {
            findViewById<ImageView>(R.id.iv_result_img).setImageResource(resId)
        }

        val descView = findViewById<TextView>(R.id.tv_result_desc)
        val fullDesc = finalResult.description
        val spannable = android.text.SpannableStringBuilder(fullDesc)

        val pattern = java.util.regex.Pattern.compile("\\[.*?\\]")
        val matcher = pattern.matcher(fullDesc)
        while (matcher.find()) {
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                matcher.start(),
                matcher.end(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFFF85A1")),
                matcher.start(),
                matcher.end(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        descView.text = spannable

        findViewById<TextView>(R.id.tv_result_tags).text = finalResult.tags.joinToString(" ") { "#$it" }

        findViewById<TextView>(R.id.tv_result_pros).text = finalResult.pros.joinToString("\n") { "• $it" }
        findViewById<TextView>(R.id.tv_result_cons).text = finalResult.cons.joinToString("\n") { "• $it" }
        findViewById<TextView>(R.id.tv_result_match).text = finalResult.matchPartner
        findViewById<TextView>(R.id.tv_result_tip).text = finalResult.tip

        findViewById<ProgressBar>(R.id.pb_emotional).progress = (scores[ScoringType.EMOTIONAL] ?: 0) * 10
        findViewById<ProgressBar>(R.id.pb_clingy).progress = (scores[ScoringType.CLINGY] ?: 0) * 10
        findViewById<ProgressBar>(R.id.pb_leading).progress = (scores[ScoringType.LEADING] ?: 0) * 10
        findViewById<ProgressBar>(R.id.pb_free).progress = (scores[ScoringType.FREE] ?: 0) * 10
    }

    private fun calculateResult(): ResultType {
        val sortedScores = scores.toList().sortedByDescending { it.second }
        val topType = sortedScores[0].first
        val secondType = sortedScores[1].first
        val topScore = sortedScores[0].second
        val secondScore = sortedScores[1].second

        val resultId = when {
            (topScore - sortedScores.last().second) <= 2 -> "BALANCED"
            (topScore - secondScore) <= 2 -> {
                when {
                    (topType == ScoringType.EMOTIONAL && secondType == ScoringType.CLINGY) ||
                    (topType == ScoringType.CLINGY && secondType == ScoringType.EMOTIONAL) -> "EMO_CLINGY"
                    (topType == ScoringType.EMOTIONAL && secondType == ScoringType.LEADING) ||
                    (topType == ScoringType.LEADING && secondType == ScoringType.EMOTIONAL) -> "EMO_LEADING"
                    (topType == ScoringType.FREE && secondType == ScoringType.LEADING) ||
                    (topType == ScoringType.LEADING && secondType == ScoringType.FREE) -> "DISTANCE"
                    else -> topType.name
                }
            }
            else -> topType.name
        }

        return AppData.results.find { it.id == resultId } ?: AppData.results[0]
    }

    // --- EXISTING TEST CARD SHARE/SAVE LOGIC ---

    private fun processCardAction(isSave: Boolean) {
        val result = lastResult ?: return

        showLoadingScreen("이미지를 만드는 중...")

        layoutLoading.postDelayed({
            val bitmap = createCardBitmap(result)
            layoutLoading.visibility = View.GONE
            layoutResult.visibility = View.VISIBLE

            if (isSave) {
                saveBitmapToGallery(bitmap)
            } else {
                shareBitmap(bitmap, result)
            }
        }, 800)
    }

    private fun createCardBitmap(result: ResultType): Bitmap {
        val view = LayoutInflater.from(this).inflate(R.layout.view_share_card, null)

        view.findViewById<TextView>(R.id.tv_card_result_title).text = result.title
        view.findViewById<TextView>(R.id.tv_card_result_subtitle).text = result.subtitle

        val resId = resources.getIdentifier(result.id.lowercase(), "drawable", packageName)
        if (resId != 0) {
            view.findViewById<ImageView>(R.id.iv_card_result_img).setImageResource(resId)
        }

        view.findViewById<TextView>(R.id.tv_card_result_desc).text = result.shareMsg
        view.findViewById<TextView>(R.id.tv_card_result_tags).text = result.tags.joinToString(" ") { "#$it" }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "LoveTest_${System.currentTimeMillis()}.png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                imageUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Toast.makeText(this, "이미지가 갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
                } ?: throw Exception("Failed to create MediaStore entry")

            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, filename)
                FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                Toast.makeText(this, "이미지가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "저장에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareBitmap(bitmap: Bitmap, result: ResultType) {
        try {
            val cachePath = File(cacheDir, "images")
            if (!cachePath.exists()) cachePath.mkdirs()

            val imageFile = File(cachePath, "result_card.png")
            FileOutputStream(imageFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "나의 연애 성격은 [${result.title}]! 너도 해봐 ❤️\n#연애성격테스트 #심리테스트")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "이미지 공유하기"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
        // Check date in foreground to handle midnight transition
        val todayKey = scheduler.getTodayDateKey()
        if (currentDailyDateKey != todayKey) {
            if (currentScreen == ScreenState.HOME) {
                updateHomeScreen()
            }
        }
    }

    override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }
}
