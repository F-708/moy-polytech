package com.politex

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.politex.ui.screens.navigateToTab
import com.politex.ui.screens.scrollRoutePattern
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.ROUTE_BELLS
import com.politex.ui.viewmodel.ROUTE_COLLEGE
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.ui.viewmodel.ROUTE_SCHEDULE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Спам по выбранной вкладке не должен перезагружать экран.
 *
 * Раньше `navigateToTab` на уже открытую вкладку делал `popUpTo(старт) + navigate`:
 * запись экрана пересоздавалась, запускались заново `LaunchedEffect(Unit)`, сбрасывался
 * скролл — визуально «мелькание перезагрузки». Здесь проверяем, что вкладка не
 * пересоздаётся, а переход на другую вкладку по-прежнему работает.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class TabReclickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController
    private val bellsStarts = AtomicInteger(0)
    private val collegeStarts = AtomicInteger(0)

    @Before
    fun setUp() {
        composeRule.setContent {
            nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val currentBase = entry?.destination?.route?.substringBefore("?")
            NavHost(navController = nav, startDestination = ROUTE_SCHEDULE) {
                composable(scrollRoutePattern(ROUTE_SCHEDULE)) {
                    StandIn("Расписание")
                }
                composable(ROUTE_LESSONS) { StandIn("Уроки") }
                composable(scrollRoutePattern(ROUTE_BELLS)) {
                    LaunchedEffect(Unit) { bellsStarts.incrementAndGet() }
                    StandIn("Звонки")
                }
                composable(ROUTE_COLLEGE) {
                    LaunchedEffect(Unit) { collegeStarts.incrementAndGet() }
                    StandIn("Другое")
                }
            }
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentBase == tab.route,
                        onClick = { nav.navigateToTab(tab.route) },
                        icon = {},
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun StandIn(title: String) {
        Text(text = title)
    }

    private fun click(route: String, times: Int = 1) {
        repeat(times) {
            composeRule.onNodeWithTag("tab_$route").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun entryId(): String? {
        var id: String? = null
        composeRule.runOnUiThread { id = nav.currentBackStackEntry?.id }
        return id
    }

    private fun route(): String? {
        var r: String? = null
        composeRule.runOnUiThread { r = nav.currentDestination?.route }
        return r
    }

    @Test
    fun `двойной клик по выбранной вкладке не пересоздаёт экран`() {
        click(ROUTE_BELLS)
        assertEquals(1, bellsStarts.get())
        val idAfterFirst = entryId()

        click(ROUTE_BELLS, times = 3)

        assertEquals("экран «Звонки» перезапустился при спаме", 1, bellsStarts.get())
        assertEquals("запись экрана пересоздана", idAfterFirst, entryId())
        assertEquals(scrollRoutePattern(ROUTE_BELLS), route())
    }

    @Test
    fun `переход на другую вкладку после спама работает`() {
        click(ROUTE_BELLS, times = 3)
        click(ROUTE_COLLEGE)
        assertEquals(1, collegeStarts.get())
        assertEquals(ROUTE_COLLEGE, route())
        assertNotEquals(scrollRoutePattern(ROUTE_BELLS), route())

        click(ROUTE_COLLEGE, times = 2)
        assertEquals(1, collegeStarts.get())
    }

    @Test
    fun `спам по стартовой вкладке не пересоздаёт расписание`() {
        val startId = entryId()
        click(ROUTE_SCHEDULE, times = 3)
        assertEquals("стартовый экран не должен пересоздаваться", startId, entryId())
        assertEquals(scrollRoutePattern(ROUTE_SCHEDULE), route())
    }
}