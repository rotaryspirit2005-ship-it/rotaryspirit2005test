package com.example.schedulelink.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.schedulelink.data.FamilyRepository
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.AppViewModelFactory
import com.example.schedulelink.ui.auth.FamilySettingsScreen
import com.example.schedulelink.ui.detail.ScheduleDetailScreen
import com.example.schedulelink.ui.detail.ScheduleDetailViewModel
import com.example.schedulelink.ui.edit.ScheduleEditScreen
import com.example.schedulelink.ui.edit.ScheduleEditViewModel
import com.example.schedulelink.ui.flow.WholeTreeFlowScreen
import com.example.schedulelink.ui.flow.WholeTreeViewModel
import com.example.schedulelink.ui.goal.GoalDetailScreen
import com.example.schedulelink.ui.goal.GoalDetailViewModel
import com.example.schedulelink.ui.goal.GoalEditScreen
import com.example.schedulelink.ui.goal.GoalEditViewModel
import com.example.schedulelink.ui.goal.GoalListScreen
import com.example.schedulelink.ui.goal.GoalListViewModel
import com.example.schedulelink.ui.importing.ImportCalendarScreen
import com.example.schedulelink.ui.importing.ImportIcsScreen
import com.example.schedulelink.ui.list.ScheduleListScreen
import com.example.schedulelink.ui.list.ScheduleListViewModel
import com.example.schedulelink.ui.milestone.MilestoneDetailScreen
import com.example.schedulelink.ui.milestone.MilestoneDetailViewModel
import com.example.schedulelink.ui.milestone.MilestoneEditScreen
import com.example.schedulelink.ui.milestone.MilestoneEditViewModel
import com.example.schedulelink.ui.month.MonthScreen
import com.example.schedulelink.ui.month.MonthViewModel
import com.example.schedulelink.ui.week.WeekScreen
import com.example.schedulelink.ui.week.WeekViewModel
import java.time.LocalDate

private const val ROUTE_MONTH = "month"
private const val ROUTE_WEEK = "week/{date}"
private const val ROUTE_LIST = "list?date={date}"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_EDIT = "edit?id={id}&milestoneId={milestoneId}"
private const val ROUTE_GOALS = "goals"
private const val ROUTE_WHOLE_TREE = "wholeTree"
private const val ROUTE_GOAL_EDIT = "goalEdit?id={id}"
private const val ROUTE_GOAL_DETAIL = "goalDetail/{id}"
private const val ROUTE_MILESTONE_EDIT = "milestoneEdit/{goalId}?id={id}"
private const val ROUTE_MILESTONE_DETAIL = "milestoneDetail/{id}"
private const val ROUTE_FAMILY_SETTINGS = "familySettings"
private const val ROUTE_IMPORT_ICS = "importIcs"
private const val ROUTE_IMPORT_CALENDAR = "importCalendar"

/** すべての画面遷移に使う既定のフェード+スケールアニメーション。 */
private val defaultEnter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))
private val defaultExit = fadeOut(tween(180))
private val defaultPopExit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScheduleNavHost(
    repository: ScheduleRepository,
    goalRepository: GoalRepository,
    milestoneRepository: MilestoneRepository,
    uid: String,
    familyId: String,
    familyRepository: FamilyRepository,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val factory = remember { AppViewModelFactory(repository, goalRepository, milestoneRepository) }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = ROUTE_MONTH,
            enterTransition = { defaultEnter },
            exitTransition = { defaultExit },
            popEnterTransition = { defaultEnter },
            popExitTransition = { defaultPopExit }
        ) {
            composable(ROUTE_MONTH) {
                val vm: MonthViewModel = viewModel(factory = factory)
                MonthScreen(
                    viewModel = vm,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onDayClick = { date -> navController.navigate("week/$date") },
                    onScheduleClick = { id -> navController.navigate("detail/$id") },
                    onGoalMapClick = { navController.navigate(ROUTE_WHOLE_TREE) },
                    onFamilySettingsClick = { navController.navigate(ROUTE_FAMILY_SETTINGS) },
                    onImportIcsClick = { navController.navigate(ROUTE_IMPORT_ICS) },
                    onImportCalendarClick = { navController.navigate(ROUTE_IMPORT_CALENDAR) }
                )
            }

            composable(
                ROUTE_WEEK,
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { backStackEntry ->
                val date = LocalDate.parse(backStackEntry.arguments!!.getString("date")!!)
                val vm: WeekViewModel = viewModel(factory = factory)
                WeekScreen(
                    viewModel = vm,
                    initialDate = date,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onDayClick = { d -> navController.navigate("list?date=$d") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                ROUTE_LIST,
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { backStackEntry ->
                val dateArg = backStackEntry.arguments?.getString("date")?.let { LocalDate.parse(it) }
                val vm: ScheduleListViewModel = viewModel(factory = factory)
                LaunchedEffect(dateArg) {
                    dateArg?.let { vm.selectDate(it) }
                }
                ScheduleListScreen(
                    viewModel = vm,
                    initialDate = dateArg ?: LocalDate.now(),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onAddClick = { navController.navigate("edit") },
                    onItemClick = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                ROUTE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getString("id")!!
            val vm: ScheduleDetailViewModel = viewModel(factory = factory)
            ScheduleDetailScreen(
                scheduleId = id,
                viewModel = vm,
                onEdit = { editId -> navController.navigate("edit?id=$editId") },
                onBack = { navController.popBackStack() },
                onLinkedClick = { linkedId -> navController.navigate("detail/$linkedId") }
            )
        }

        composable(
            ROUTE_EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("milestoneId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val milestoneId = backStackEntry.arguments?.getString("milestoneId")
            val vm: ScheduleEditViewModel = viewModel(factory = factory)
            ScheduleEditScreen(
                scheduleId = id,
                initialMilestoneId = milestoneId,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_WHOLE_TREE) {
            val vm: WholeTreeViewModel = viewModel(factory = factory)
            WholeTreeFlowScreen(
                viewModel = vm,
                onGoalClick = { id -> navController.navigate("goalDetail/$id") },
                onMilestoneClick = { id -> navController.navigate("milestoneDetail/$id") },
                onScheduleClick = { id -> navController.navigate("detail/$id") },
                onAddGoalClick = { navController.navigate("goalEdit") },
                onGoalListClick = { navController.navigate(ROUTE_GOALS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_GOALS) {
            val vm: GoalListViewModel = viewModel(factory = factory)
            GoalListScreen(
                viewModel = vm,
                onAddClick = { navController.navigate("goalEdit") },
                onItemClick = { id -> navController.navigate("goalDetail/$id") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            ROUTE_GOAL_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val vm: GoalEditViewModel = viewModel(factory = factory)
            GoalEditScreen(
                goalId = id,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            ROUTE_GOAL_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getString("id")!!
            val vm: GoalDetailViewModel = viewModel(factory = factory)
            GoalDetailScreen(
                goalId = id,
                viewModel = vm,
                onEdit = { editId -> navController.navigate("goalEdit?id=$editId") },
                onBack = { navController.popBackStack() },
                onAddMilestone = { goalId -> navController.navigate("milestoneEdit/$goalId") },
                onMilestoneClick = { milestoneId -> navController.navigate("milestoneDetail/$milestoneId") }
            )
        }

        composable(
            ROUTE_MILESTONE_EDIT,
            arguments = listOf(
                navArgument("goalId") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments!!.getString("goalId")!!
            val id = backStackEntry.arguments?.getString("id")
            val vm: MilestoneEditViewModel = viewModel(factory = factory)
            MilestoneEditScreen(
                goalId = goalId,
                milestoneId = id,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            ROUTE_MILESTONE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getString("id")!!
            val vm: MilestoneDetailViewModel = viewModel(factory = factory)
            MilestoneDetailScreen(
                milestoneId = id,
                viewModel = vm,
                onEdit = { goalId, editId -> navController.navigate("milestoneEdit/$goalId?id=$editId") },
                onBack = { navController.popBackStack() },
                onAddSchedule = { milestoneId -> navController.navigate("edit?milestoneId=$milestoneId") },
                onScheduleClick = { scheduleId -> navController.navigate("detail/$scheduleId") }
            )
        }

        composable(ROUTE_FAMILY_SETTINGS) {
            FamilySettingsScreen(
                uid = uid,
                familyId = familyId,
                familyRepository = familyRepository,
                onBack = { navController.popBackStack() },
                onLeft = {},
                onSignOut = onSignOut
            )
        }

        composable(ROUTE_IMPORT_ICS) {
            ImportIcsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_IMPORT_CALENDAR) {
            ImportCalendarScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        }
    }
}
