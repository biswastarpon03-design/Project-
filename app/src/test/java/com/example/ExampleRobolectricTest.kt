package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChannelFeedRepository
import com.example.model.AppTab
import com.example.model.ChannelPlatform
import com.example.model.FeedFilter
import com.example.model.FeedLoadState
import com.example.model.FeedPost
import com.example.model.PostResponse
import com.example.model.ProjectPiChannels
import com.example.model.QuestionPostHelper
import com.example.ui.ProjectPiScreen
import com.example.ui.ProjectPiUiState
import com.example.ui.ProjectPiViewModel
import com.example.ui.theme.ProjectPiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `app name matches Project pi`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Project π", appName)
  }

  @Test
  fun `official channel urls and tabs match specification`() {
    val urlsById = ProjectPiChannels.all.associate { it.id to it.url }
    assertEquals(3, urlsById.size)
    assertEquals(
      "https://whatsapp.com/channel/0029VbDBwQp4Y9lhHdsmHG2D",
      urlsById["whatsapp"],
    )
    assertEquals(
      "https://www.facebook.com/share/1ddaQL72TH/",
      urlsById["facebook"],
    )
    assertEquals(
      "https://t.me/projectpitelegram",
      urlsById["telegram"],
    )
    assertEquals("telegram", ProjectPiChannels.forTab(AppTab.TELEGRAM)?.id)
    assertEquals("whatsapp", ProjectPiChannels.forTab(AppTab.WHATSAPP)?.id)
    assertEquals("facebook", ProjectPiChannels.forTab(AppTab.FACEBOOK)?.id)
  }

  @Test
  fun `parses telegram channel html into native feed posts`() {
    val sampleHtml =
      """
      <div class="tgme_channel_info_header_title"><span dir="auto">Project π</span></div>
      <div class="tgme_channel_info_counters"><span class="counter_value">1.2K</span> <span class="counter_type">subscribers</span></div>
      <div class="tgme_channel_info_description">Official updates</div>
      <div class="tgme_widget_message_wrap js-widget_message_wrap">
        <div class="tgme_widget_message" data-post="projectpitelegram/42">
          <div class="tgme_widget_message_text js-message_text" dir="auto">Hello community!<br/>New release out now.</div>
          <span class="tgme_widget_message_views">340</span>
          <time datetime="2026-11-20T10:15:00+00:00" class="time">10:15</time>
        </div>
      </div>
      """.trimIndent()

    val parsed = ChannelFeedRepository.parseTelegramChannelHtml(sampleHtml)
    assertEquals("Project π", parsed.preview.liveTitle)
    assertEquals("1.2K", parsed.preview.subscriberCount)
    assertEquals("Official updates", parsed.preview.liveDescription)
    assertEquals(1, parsed.posts.size)
    val post = parsed.posts.first()
    assertEquals(listOf(ChannelPlatform.TELEGRAM), post.platforms)
    assertEquals("https://t.me/projectpitelegram/42", post.primaryUrl)
    assertEquals("340", post.viewCount)
    assertEquals("2026-11-20 • 10:15", post.timestampLabel)
    assertEquals("Hello community!\nNew release out now.", post.text)
  }

  @Test
  fun `parses whatsapp and facebook posts and filters login boilerplate`() {
    val waHtml =
      """
      <html><head>
        <meta property="og:title" content="Project π WhatsApp" />
        <meta property="og:description" content="Latest announcement from Project π on WhatsApp." />
      </head></html>
      """.trimIndent()
    val waResult = ChannelFeedRepository.parseWhatsAppChannelHtml(waHtml)
    assertEquals(1, waResult.posts.size)
    assertEquals(listOf(ChannelPlatform.WHATSAPP), waResult.posts.first().platforms)
    assertEquals("Latest announcement from Project π on WhatsApp.", waResult.posts.first().text)

    val fbBoilerplateHtml =
      """
      <html><head>
        <meta property="og:title" content="Facebook" />
        <meta property="og:description" content="Log into Facebook to start sharing and connecting with your friends." />
      </head></html>
      """.trimIndent()
    val fbBoilerplate = ChannelFeedRepository.parseFacebookChannelHtml(fbBoilerplateHtml)
    assertNull(fbBoilerplate.preview.liveTitle)
    assertEquals(0, fbBoilerplate.posts.size)
  }

  @Test
  fun `deduplicates identical and near-identical posts across all three platforms`() {
    val sharedBody = "Project π v2.0 is live now! Check out the full notes and join the community."
    val waPost =
      FeedPost(
        id = "wa_1",
        platforms = listOf(ChannelPlatform.WHATSAPP),
        platformUrls = mapOf(ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL),
        channelTitle = "Project π WhatsApp",
        text = sharedBody,
        timestampLabel = "Synced live",
        primaryUrl = ProjectPiChannels.WHATSAPP_URL,
      )
    val tgPost =
      FeedPost(
        id = "tg_1",
        platforms = listOf(ChannelPlatform.TELEGRAM),
        platformUrls = mapOf(ChannelPlatform.TELEGRAM to "https://t.me/projectpitelegram/10"),
        channelTitle = "Project π",
        text = "$sharedBody https://t.me/projectpitelegram",
        timestampLabel = "2026-11-21 • 09:00",
        viewCount = "510",
        primaryUrl = "https://t.me/projectpitelegram/10",
      )
    val fbPost =
      FeedPost(
        id = "fb_1",
        platforms = listOf(ChannelPlatform.FACEBOOK),
        platformUrls = mapOf(ChannelPlatform.FACEBOOK to ProjectPiChannels.FACEBOOK_URL),
        channelTitle = "Project π Facebook",
        text = "Project π v2.0 is live now! Check out the full notes and join the community. 🚀",
        timestampLabel = "Synced live",
        primaryUrl = ProjectPiChannels.FACEBOOK_URL,
      )
    val uniqueTgPost =
      FeedPost(
        id = "tg_2",
        platforms = listOf(ChannelPlatform.TELEGRAM),
        platformUrls = mapOf(ChannelPlatform.TELEGRAM to "https://t.me/projectpitelegram/11"),
        channelTitle = "Project π",
        text = "Weekly Q&A starts in one hour on Telegram only!",
        timestampLabel = "2026-11-21 • 10:00",
        primaryUrl = "https://t.me/projectpitelegram/11",
      )

    val computed =
      ProjectPiViewModel.computeUnifiedFeed(
        basePosts = listOf(tgPost, uniqueTgPost),
        webExtractedPosts =
          mapOf(
            ChannelPlatform.WHATSAPP to listOf(waPost),
            ChannelPlatform.FACEBOOK to listOf(fbPost),
          ),
        selectedFilter = FeedFilter.ALL,
        searchQuery = "",
      )
    assertEquals(2, computed.filteredPosts.size)
    assertEquals(
      listOf(ChannelPlatform.WHATSAPP, ChannelPlatform.TELEGRAM, ChannelPlatform.FACEBOOK),
      computed.filteredPosts[0].platforms,
    )
    assertEquals("510", computed.filteredPosts[0].viewCount)
    assertEquals("2026-11-21 • 09:00", computed.filteredPosts[0].timestampLabel)
    assertEquals(listOf(ChannelPlatform.TELEGRAM), computed.filteredPosts[1].platforms)
    assertEquals(1, computed.platformCounts[ChannelPlatform.WHATSAPP])
    assertEquals(2, computed.platformCounts[ChannelPlatform.TELEGRAM])
    assertEquals(1, computed.platformCounts[ChannelPlatform.FACEBOOK])
  }

  @Test
  fun `question detection and canonical key match across platforms`() {
    assertTrue(QuestionPostHelper.looksLikeQuestion("Daily Question: What is 2 + 2?"))
    assertTrue(QuestionPostHelper.looksLikeQuestion("Solve for x in 3x = 12"))
    assertFalse(QuestionPostHelper.looksLikeQuestion("Project π announcement for all members."))

    val postA =
      FeedPost(
        id = "wa_q",
        platforms = listOf(ChannelPlatform.WHATSAPP),
        platformUrls = mapOf(ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL),
        channelTitle = "WhatsApp",
        text = "What is the value of pi?",
        timestampLabel = "Synced live",
        primaryUrl = ProjectPiChannels.WHATSAPP_URL,
      )
    val postB =
      FeedPost(
        id = "tg_q",
        platforms = listOf(ChannelPlatform.TELEGRAM),
        platformUrls = mapOf(ChannelPlatform.TELEGRAM to ProjectPiChannels.TELEGRAM_URL),
        channelTitle = "Telegram",
        text = "What is the value of pi? https://t.me/projectpitelegram",
        timestampLabel = "2026-11-21",
        primaryUrl = ProjectPiChannels.TELEGRAM_URL,
      )
    assertEquals(
      QuestionPostHelper.computePostKey(postA),
      QuestionPostHelper.computePostKey(postB),
    )
  }

  @Test
  fun `compose screen renders question answer field and submits response`() {
    val questionPost =
      FeedPost(
        id = "q_1",
        platforms =
          listOf(ChannelPlatform.WHATSAPP, ChannelPlatform.TELEGRAM, ChannelPlatform.FACEBOOK),
        platformUrls =
          mapOf(
            ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL,
            ChannelPlatform.TELEGRAM to ProjectPiChannels.TELEGRAM_URL,
            ChannelPlatform.FACEBOOK to ProjectPiChannels.FACEBOOK_URL,
          ),
        channelTitle = "Project π",
        text = "Daily Question: What is 22/7 closest to?",
        timestampLabel = "2026-11-21 • 09:00",
        primaryUrl = ProjectPiChannels.TELEGRAM_URL,
      )
    val postKey = QuestionPostHelper.computePostKey(questionPost)
    var savedAnswer = ""
    var selectedTab = AppTab.UPDATES

    composeTestRule.setContent {
      ProjectPiTheme(darkTheme = false) {
        ProjectPiScreen(
          uiState =
            ProjectPiUiState(
              selectedTab = selectedTab,
              selectedFeedFilter = FeedFilter.ALL,
              feedState =
                FeedLoadState.Ready(posts = listOf(questionPost), previews = emptyMap()),
              unifiedPosts = listOf(questionPost),
              platformPostCounts =
                mapOf(
                  ChannelPlatform.WHATSAPP to 1,
                  ChannelPlatform.TELEGRAM to 1,
                  ChannelPlatform.FACEBOOK to 1,
                ),
              responsesByPostKey =
                mapOf(
                  postKey to
                    listOf(
                      PostResponse(
                        id = "saved_1",
                        userId = "user_1",
                        postId = questionPost.id,
                        postKey = postKey,
                        questionExcerpt = questionPost.text,
                        answerText = "Existing saved answer",
                        platformSummary = "WhatsApp, Telegram, Facebook",
                      )
                    )
                ),
            ),
          onSelectTab = { selectedTab = it },
          onSelectFeedFilter = {},
          onRefreshFeed = {},
          onWebViewPostsExtracted = { _, _ -> },
          onSaveResponse = { _, _, answer, _ -> savedAnswer = answer },
          onUpdateResponse = { _, _, _, _ -> },
          onDeleteResponse = { _, _, _ -> },
          onClearResponseError = {},
          onSearchQueryChange = {},
          onToggleJoined = {},
          onOpenDetails = {},
          onDismissDetails = {},
          onSignOut = {},
        )
      }
    }

    composeTestRule.onAllNodesWithText("Project π").get(0).assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_updates").assertIsDisplayed()
    composeTestRule.onNodeWithText("Daily Question: What is 22/7 closest to?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Existing saved answer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_input_${questionPost.id}").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("answer_input_${questionPost.id}").performTextInput("3.14159")
    composeTestRule.onNodeWithTag("submit_answer_${questionPost.id}").performScrollTo().performClick()
    assertEquals("3.14159", savedAnswer)

    composeTestRule.onNodeWithTag("nav_tab_whatsapp").performClick()
    assertEquals(AppTab.WHATSAPP, selectedTab)
  }

  @Test
  fun `compose screen adapts cleanly to system dark theme`() {
    val samplePost =
      FeedPost(
        id = "dark_1",
        platforms = listOf(ChannelPlatform.WHATSAPP, ChannelPlatform.FACEBOOK),
        platformUrls =
          mapOf(
            ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL,
            ChannelPlatform.FACEBOOK to ProjectPiChannels.FACEBOOK_URL,
          ),
        channelTitle = "Project π",
        text = "Dark theme cross-platform broadcast",
        timestampLabel = "Synced live",
        primaryUrl = ProjectPiChannels.WHATSAPP_URL,
      )

    composeTestRule.setContent {
      ProjectPiTheme(darkTheme = true) {
        ProjectPiScreen(
          uiState =
            ProjectPiUiState(
              feedState = FeedLoadState.Ready(posts = listOf(samplePost), previews = emptyMap()),
              unifiedPosts = listOf(samplePost),
              platformPostCounts =
                mapOf(
                  ChannelPlatform.WHATSAPP to 1,
                  ChannelPlatform.TELEGRAM to 0,
                  ChannelPlatform.FACEBOOK to 1,
                ),
            ),
          onSelectTab = {},
          onSelectFeedFilter = {},
          onRefreshFeed = {},
          onWebViewPostsExtracted = { _, _ -> },
          onSaveResponse = { _, _, _, _ -> },
          onUpdateResponse = { _, _, _, _ -> },
          onDeleteResponse = { _, _, _ -> },
          onClearResponseError = {},
          onSearchQueryChange = {},
          onToggleJoined = {},
          onOpenDetails = {},
          onDismissDetails = {},
          onSignOut = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Dark theme cross-platform broadcast").assertIsDisplayed()
  }
}
