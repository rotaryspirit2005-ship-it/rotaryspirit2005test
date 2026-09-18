package com.example.schedulelink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.AppViewModelFactory
import com.example.schedulelink.ui.detail.ScheduleDetailScreen
import com.example.schedulelink.ui.detail.ScheduleDetailViewModel
import com.example.schedulelink.ui.edit.ScheduleEditScreen
import com.example.schedulelink.ui.edit.ScheduleEditViewModel
import com.example.schedulelink.ui.goal.GoalDetailScreen
import com.example.schedulelink.ui.goal.GoalDetailViewModel
import com.example.schedulelink.ui.goal.GoalEditScreen
import com.example.schedulelink.ui.goal.GoalEditViewModel
import com.example.schedulelink.ui.goal.GoalListScreen
import com.example.schedulelink.ui.goal.GoalListViewModel
import com.example.schedulelink.ui.list.ScheduleListScreen
import com.example.schedulelink.ui.list.ScheduleListViewModel
import com.example.schedulelink.ui.milestone.MilestoneDetailScreen
import com.example.schedulelink.ui.milestone.MilestoneDetailViewModel
import com.example.schedulelink.ui.milestone.MilestoneEditScreen
import com.example.schedulelink.ui.milestone.MilestoneEditViewModel

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_EDIT = "edit?id={id}&milestoneId={milestoneId}"
private const val ROUTE_GOALS = "goals"
private const val ROUTE_GOAL_EDIT = "goalEdit?id={id}"
private const val ROUTE_GOAL_DETAIL = "goalDetail/{id}"
private const val ROUTE_MILESTONE_EDIT = "milestoneEdit/{goalId}?id={id}"
private const val ROUTE_MILESTONE_DETAIL = "milestoneDetail/{id}"

@Composable
fun ScheduleNavHost(
    repository: ScheduleRepository,
    goalRepository: GoalRepository,
    milestoneRepository: MilestoneRepository
) {
    val navController = rememberNavController()
    val factory = remember { AppViewModelFactory(repository, goalRepository, milestoneRepository) }

    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            val vm: ScheduleListViewModel = viewModel(factory = factory)
            ScheduleListScreen(
                viewModel = vm,
                onAddClick = { navController.navigate("edit") },
                onItemClick = { id -> navController.navigate("detail/$id") },
                onGoalMapClick = { navController.navigate(ROUTE_GOALS) }
            )
        }

        composable(
            ROUTE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getLong("id")
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
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("milestoneId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val idArg = backStackEntry.arguments!!.getLong("id")
            val milestoneIdArg = backStackEntry.arguments!!.getLong("milestoneId")
            val id = if (idArg == -1L) null else idArg
            val milestoneId = if (milestoneIdArg == -1L) null else milestoneIdArg
            val vm: ScheduleEditViewModel = viewModel(factory = factory)
            ScheduleEditScreen(
                scheduleId = id,
                initialMilestoneId = milestoneId,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
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
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val idArg = backStackEntry.arguments!!.getLong("id")
            val id = if (idArg == -1L) null else idArg
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
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getLong("id")
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
                navArgument("goalId") { type = NavType.LongType },
                navArgument("id") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments!!.getLong("goalId")
            val idArg = backStackEntry.arguments!!.getLong("id")
            val id = if (idArg == -1L) null else idArg
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
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getLong("id")
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
    }
}
