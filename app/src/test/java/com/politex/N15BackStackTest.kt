package com.politex

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.politex.ui.screens.buildGroupScheduleRoute
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildTeacherRoute
import com.politex.ui.screens.navigateToTab
import com.politex.ui.screens.scrollRoutePattern
import com.politex.ui.viewmodel.ARG_SCROLL
import com.politex.ui.viewmodel.ROUTE_BELLS
import com.politex.ui.viewmodel.ROUTE_COLLEGE
import com.politex.ui.viewmodel.ROUTE_GROUP_SCHEDULE
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.ui.viewmodel.ROUTE_MAP
import com.politex.ui.viewmodel.ROUTE_SCHEDULE
import com.politex.ui.viewmodel.ROUTE_TEACHER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class N15BackStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private val scrollArgs =
        listOf(
            navArgument(ARG_SCROLL) {
                type = NavType.StringType
                defaultValue = ""
            },
        )
    private val mapArgs =
        listOf(
            navArgument("room") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("from") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("route") {
                type = NavType.BoolType
                defaultValue = false
            },
        )
    private val teacherArgs =
        listOf(
            navArgument("name") {
                type = NavType.StringType
                defaultValue = ""
            },
        )
    private val groupArgs =
        listOf(
            navArgument("group") {
                type = NavType.StringType
                defaultValue = ""
            },
        )

    @Before
    fun setUp() {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = ROUTE_SCHEDULE) {
                composable(scrollRoutePattern(ROUTE_SCHEDULE), arguments = scrollArgs) { Text("schedule") }
                composable(ROUTE_LESSONS) { Text("lessons") }
                composable(scrollRoutePattern(ROUTE_BELLS), arguments = scrollArgs) { Text("bells") }
                composable(ROUTE_COLLEGE) { Text("college") }
                composable(ROUTE_MAP, arguments = mapArgs) { Text("map") }
                composable(ROUTE_TEACHER, arguments = teacherArgs) { Text("teacher") }
                composable(ROUTE_GROUP_SCHEDULE, arguments = groupArgs) { Text("group_schedule") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun go(action: NavHostController.() -> Unit) {
        composeRule.runOnUiThread { nav.action() }
        composeRule.waitForIdle()
    }

    private fun current(): NavBackStackEntry? {
        var entry: NavBackStackEntry? = null
        composeRule.runOnUiThread { entry = nav.currentBackStackEntry }
        return entry
    }

    @Test
    fun `N15 - расписание группы - кабинет - карта - назад возвращает ТУ ЖЕ группу`() {
        go { navigateToTab(ROUTE_COLLEGE) }
        go { navigate(buildGroupScheduleRoute(null)) }
        go { navigate(buildGroupScheduleRoute("41О")) }
        go { navigate(buildMapRoute("214", "", false)) }

        assertEquals(ROUTE_MAP, current()?.destination?.route)

        go { popBackStack() }
        val restored = current()
        assertEquals(ROUTE_GROUP_SCHEDULE, restored?.destination?.route)
        assertEquals("41О", restored?.arguments?.getString("group"))

        go { popBackStack() }
        assertEquals("", current()?.arguments?.getString("group"))

        go { popBackStack() }
        assertEquals(ROUTE_COLLEGE, current()?.destination?.route)
    }

    @Test
    fun `карта - преподаватель - назад - та же карта с тем же кабинетом`() {
        go { navigate(buildMapRoute("214", null, false)) }
        go { navigate(buildTeacherRoute("Иванова Мария Петровна")) }
        assertEquals(ROUTE_TEACHER, current()?.destination?.route)
        assertEquals(
            "Иванова Мария Петровна",
            current()?.arguments?.getString("name"),
        )

        go { popBackStack() }
        val map = current()
        assertEquals(ROUTE_MAP, map?.destination?.route)
        assertEquals("214", map?.arguments?.getString("room"))
        assertNull(map?.arguments?.getString("from"))
    }

    @Test
    fun `переключение вкладок не плодит хвост - назад со стартового таба выходит из приложения`() {
        go { navigateToTab(ROUTE_BELLS) }
        go { navigate(buildMapRoute("214", null, false)) }
        go { navigateToTab(ROUTE_COLLEGE) }
        go { navigateToTab(ROUTE_SCHEDULE) }

        assertEquals(scrollRoutePattern(ROUTE_SCHEDULE), current()?.destination?.route)
        var popped = true
        composeRule.runOnUiThread { popped = nav.popBackStack() }
        composeRule.waitForIdle()
        assertFalse("back со стартового таба должен выходить из приложения", popped)
    }

    @Test
    fun `расписание - преподаватель - назад - тот же таб расписания`() {
        go { navigate(buildTeacherRoute("Петров Пётр")) }
        go { popBackStack() }
        assertEquals(scrollRoutePattern(ROUTE_SCHEDULE), current()?.destination?.route)
    }
}
