package com.example.schedulelink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.AppViewModelFactory
import com.example.schedulelink.ui.detail.ScheduleDetailScreen
import com.example.schedulelink.ui.detail.ScheduleDetailViewModel
import com.example.schedulelink.ui.edit.ScheduleEditScreen
import com.example.schedulelink.ui.edit.ScheduleEditViewModel
import com.example.schedulelink.ui.list.ScheduleListScreen
import com.example.schedulelink.ui.list.ScheduleListViewModel

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_EDIT = "edit?id={id}"

@Composable
fun ScheduleNavHost(repository: ScheduleRepository) {
    val navController = rememberNavController()
    val factory = remember { AppViewModelFactory(repository) }

    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            val vm: ScheduleListViewModel = viewModel(factory = factory)
            ScheduleListScreen(
                viewModel = vm,
                onAddClick = { navController.navigate("edit") },
                onItemClick = { id -> navController.navigate("detail/$id") }
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
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val idArg = backStackEntry.arguments!!.getLong("id")
            val id = if (idArg == -1L) null else idArg
            val vm: ScheduleEditViewModel = viewModel(factory = factory)
            ScheduleEditScreen(
                scheduleId = id,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
