package com.kanedias.archforums

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.forEach
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.*
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.kanedias.archforums.markdown.mdRendererFrom
import com.kanedias.archforums.misc.showFullscreenFragment
import com.kanedias.archforums.model.MainPageModel
import com.kanedias.archforums.service.Config
import com.kanedias.archforums.service.Network
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.IllegalStateException

/**
 * Main activity of the application. Has toolbar and navigation drawer to allow login and search shortcuts.
 * All fragment transactions happen here.
 *
 * @author Kanedias
 *
 * Created on 2019-12-29
 */
class MainActivity : ThemedActivity() {

    @BindView(R.id.main_drawer_area)
    lateinit var drawer: DrawerLayout

    @BindView(R.id.main_sidebar)
    lateinit var sidebar: NavigationView

    @BindView(R.id.main_toolbar)
    lateinit var toolbar: Toolbar

    lateinit var sidebarHeader: SidebarHeaderViewHolder

    private lateinit var mainPageModel: MainPageModel

    private lateinit var donateHelper: DonateHelper

    private lateinit var accountResetListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountResetListener = object: SharedPreferences.OnSharedPreferenceChangeListener {

            private var savedHomeUrl = Config.homeUrl

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
                if (key != Config.HOME_URL)
                    return

                if (savedHomeUrl == Config.homeUrl)
                    return

                savedHomeUrl = Config.homeUrl
                mainPageModel.account.value = null
            }
        }
        Config.prefs.registerOnSharedPreferenceChangeListener(accountResetListener)

        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        // setup action bar
        setSupportActionBar(toolbar)

        // setup donate helper
        donateHelper = DonateHelper(this)

        // setup sidebar
        sidebar.menu.forEach { it.isEnabled = false }
        sidebar.setNavigationItemSelectedListener { item -> onSidebarItemSelected(item) }
        sidebarHeader = SidebarHeaderViewHolder(sidebar.getHeaderView(0))

        // setup drawer and menu button
        val drawerToggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.open, R.string.close)
        drawer.addDrawerListener(drawerToggle)
        drawer.addDrawerListener(object: DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                val resources = listOf(
                    R.drawable.guy_fawkes_mask,
                    R.drawable.incognito,
                    R.drawable.bomb,
                    R.drawable.television,
                    R.drawable.cinema,
                    R.drawable.nuke)
                sidebarHeader.randomImage.setImageResource(resources.random())
            }
        })
        drawerToggle.syncState()

        mainPageModel = ViewModelProviders.of(this).get(MainPageModel::class.java)
        mainPageModel.account.observe(this, Observer {
            if (it.isNullOrEmpty()) {
                sidebar.menu.forEach { item -> item.isEnabled = false }
                sidebarHeader.username.setText(R.string.guest)
                sidebarHeader.loginButton.setImageResource(R.drawable.login)
                sidebarHeader.loginButton.setOnClickListener {
                    drawer.closeDrawers()
                    showFullscreenFragment(LoginFragment())
                }
            } else {
                sidebar.menu.forEach { item -> item.isEnabled = true }
                sidebarHeader.username.text = it
                sidebarHeader.loginButton.setImageResource(R.drawable.exit)
                sidebarHeader.loginButton.setOnClickListener {
                    drawer.closeDrawers()
                    Network.logout()
                    refreshContent()
                }
            }
        })

        refreshContent()

        // if activity was started by clicking a link, handle it here
        handleIntent(intent)

        // clear intent data so use won't be redirected to old url
        // in case activity is re-created
        intent.data = null
    }

    override fun onStart() {
        super.onStart()

        checkWhatsNew()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_action_bar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        setupTopSearch(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_donate -> donateHelper.donate()
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.menu_about -> startActivity(Intent(this, AboutActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }

        // it was handled in `when` block or we wouldn't be at this point
        // confirm it
        return true
    }

    private fun setupTopSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.menu_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrEmpty())
                    return true

                val frag = SearchMessagesContentFragment().apply {
                    arguments = Bundle().apply { putString(SearchMessagesContentFragment.KEYWORD_ARG, query) }
                }
                showFullscreenFragment(frag)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }

        })
    }

    private fun checkWhatsNew() {
        data class Release(val versionName: String, val textId: Int)

        val releases = mapOf(
            1 to Release("1.0.0", R.string.release_1),
            3 to Release("1.0.3", R.string.release_3),
            4 to Release("1.0.4", R.string.release_4),
            5 to Release("1.1.0", R.string.release_5),
            8 to Release("1.2.0", R.string.release_8),
            15 to Release("1.2.7", R.string.release_15),
            16 to Release("1.2.8", R.string.release_16)
        )

        val currVersion = BuildConfig.VANILLA_VERSION_CODE
        if (Config.lastVersion == 0) {
            // first time opening the app, don't show what's new at all
            Config.lastVersion = currVersion
        }

        // check how many releases we missed
        if (Config.lastVersion < currVersion) {
            val whatsNew = StringBuilder(150)
            for(missedRelease in currVersion downTo Config.lastVersion + 1) {
                if (!releases.containsKey(missedRelease)) {
                    // no info on that release, probably internal bugfix or refactoring
                    // (or current version is very old)
                    continue
                }

                val release = releases.getValue(missedRelease)
                whatsNew.append("${release.versionName}\n")
                whatsNew.append("----------------------\n")

                val parts = getString(release.textId).split("\n").map(String::trim)
                parts.forEach {
                    whatsNew.append("- $it\n")
                }

                whatsNew.append("\n\n")
            }

            if (whatsNew.isNotEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.whats_new)
                    .setMessage(mdRendererFrom(this).toMarkdown(whatsNew.toString()))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.help_the_project) { _, _ -> donateHelper.donate() }
                    .show()
            }
        }

        // set last version anyway. Protects from big version code jumps
        Config.lastVersion = currVersion
    }

    private fun onSidebarItemSelected(item: MenuItem): Boolean {
        val url = when (item.itemId) {
            R.id.menu_item_my_messages -> {
                // special case, that's a message search page, not a topic one
                val frag = SearchMessagesContentFragment().apply {
                    arguments = Bundle().apply { putString(SearchMessagesContentFragment.URL_ARG, Network.OWN_MESSAGES_URL) }
                }
                showFullscreenFragment(frag)
                return true
            }
            R.id.menu_item_my_topics -> Network.OWN_TOPICS_URL
            R.id.menu_item_replies -> Network.REPLIES_TOPICS_URL
            R.id.menu_item_new_messages -> Network.NEW_MESSAGES_TOPICS_URL
            R.id.menu_item_recent -> Network.RECENT_TOPICS_URL
            R.id.menu_item_my_subscriptions -> Network.SUBSCRIBED_TOPICS_URL
            else -> throw IllegalStateException("No such page!")
        }
        drawer.closeDrawers()

        val frag = SearchTopicsContentFragment().apply {
            arguments = Bundle().apply { putString(SearchTopicsContentFragment.URL_ARG, url) }
        }
        showFullscreenFragment(frag)

        return true
    }

    override fun onNewIntent(received: Intent) {
        super.onNewIntent(received)
        handleIntent(received)
    }

    /**
     * Handle the passed intent. This is invoked whenever we need to actually react to the intent that was
     * passed to this activity, this can be just activity start from the app manager, click on a link or
     * on a notification belonging to this app
     * @param cause the passed intent. It will not be modified within this function.
     */
    private fun handleIntent(cause: Intent?) {
        if (cause == null)
            return

        when (cause.action) {
            Intent.ACTION_VIEW -> {
                // try to detect if it's someone trying to open the website link with us
                val meta = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
                val websiteUrl = meta.getString("mainWebsiteUrl", null)!!

                if (cause.data?.toString()?.contains(websiteUrl) == true) {
                    consumeCallingUrl(cause)
                }
            }
        }
    }

    /**
     * Take URI from the activity's intent, try to shape it into something usable
     * and handle the action user requested in it if possible. E.g. clicking on link
     * https://<forum-url>/viewtopic.php?... should open that topic or forum inside the app so try
     * to guess what user wanted with it as much as possible.
     *
     * This is also a routing that is responsible for highlighting the messages inside the topics
     * if the url to topic meant to highlight it.
     */
    private fun consumeCallingUrl(cause: Intent) {
        try {
            val url = cause.data ?: return
            val address = url.pathSegments // it's in the form of /viewxxx.php?query=parameters

            when(address[0]) {
                "viewforum.php" -> {
                    // e.g https://<website>/viewforum.php?id=2&p=3
                    val forumId = url.getQueryParameter("id")?.toIntOrNull() ?: return // forum query must contain id

                    // try to find fragment with this forum, if it's last one, launch it
                    val contentStack = supportFragmentManager.fragments.filterIsInstance<ContentFragment>()
                    val last = contentStack.lastOrNull()
                    if (last is ForumContentFragment && last.contents.forum.value?.id == forumId) {
                        // this is our fragment, open link in it
                        last.requireArguments().putSerializable(ForumContentFragment.URL_ARG, url.toString())
                        last.refreshContent()
                        return
                    }

                    // no fragment with this forum on top, open it
                    val fragment = ForumContentFragment().apply {
                        arguments = Bundle().apply {
                            putString(ForumContentFragment.URL_ARG, url.toString())
                        }
                    }
                    showFullscreenFragment(fragment)
                }
                "viewtopic.php" -> {
                    // e.g https://<website>/viewtopic.php?pid=XXXXXXXX#pXXXXXXXX
                    // or https://<website>/viewtopic.php?id=XXXXX&p=XX

                    val topicId = url.getQueryParameter("id")?.toIntOrNull()
                    if (topicId != null) {
                        // it's a viewtopic.php?id=3396&p=15 style link

                        // try to find fragment with this topic, if it's last one, launch it
                        val contentStack = supportFragmentManager.fragments.filterIsInstance<ContentFragment>()
                        val last = contentStack.lastOrNull()
                        if (last is TopicContentFragment && last.contents.topic.value?.id == topicId) {
                            // this is our fragment, open link in it
                            last.requireArguments().putSerializable(TopicContentFragment.URL_ARG, url.toString())
                            last.refreshContent()
                            return
                        }
                    }

                    val messageId = url.getQueryParameter("pid")?.toIntOrNull()
                    if (messageId != null) {
                        // try to find fragment with this forum, if it's last one, launch it
                        val contentStack = supportFragmentManager.fragments.filterIsInstance<ContentFragment>()
                        val last = contentStack.lastOrNull()
                        if (last is TopicContentFragment && last.contents.topic.value?.messages?.any { it.id == messageId } == true) {
                            // highlight the message
                            last.highlightMessage(messageId)
                            return
                        }
                    }

                    // no fragment with this topic on top, open it
                    val fragment = TopicContentFragment().apply {
                        arguments = Bundle().apply {
                            putString(TopicContentFragment.URL_ARG, url.toString())
                        }
                    }
                    showFullscreenFragment(fragment)
                    return
                }
                else -> return
            }
        } catch (ex: Exception) {
            Network.reportErrors(this, ex)
        }
    }

    private fun refreshContent() {
        if (Network.isLoggedIn()) {
            mainPageModel.account.value = Network.getUsername()

            // re-login in background if needed
            if (Network.daysToAuthExpiration() < 3) {
                lifecycleScope.launch { Network.perform({ Network.refreshLogin() }) }
            }
        } else {
            // not logged in, show guest name
            mainPageModel.account.value = null
        }
    }

    class SidebarHeaderViewHolder(iv: View) {
        @BindView(R.id.sidebar_header_random_image)
        lateinit var randomImage: ImageView

        @BindView(R.id.sidebar_header_current_user_name)
        lateinit var username: TextView

        @BindView(R.id.sidebar_header_login)
        lateinit var loginButton: ImageView

        init {
            ButterKnife.bind(this, iv)
        }
    }

}
