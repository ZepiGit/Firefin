package zepigit.firefin.app.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.MediaItem

/** MENU-key actions on a Jellyfin card: marks, favorites, resume reset and the jump to the series. */
object CardMenu {
    enum class Action(@StringRes val label: Int) {
        REMOVE_FROM_RESUME(R.string.menu_remove_resume),
        MARK_PLAYED(R.string.menu_mark_played),
        MARK_UNPLAYED(R.string.menu_mark_unplayed),
        FAVORITE_ADD(R.string.menu_favorite_add),
        FAVORITE_REMOVE(R.string.menu_favorite_remove),
        GO_TO_SERIES(R.string.menu_go_series),
    }

    /** Resume reset needs the 10.9+ user-data route, so a 10.8 server does not offer it. */
    fun entries(item: MediaItem, legacyRoutes: Boolean): List<Action> = buildList {
        if (item.resumeTicks > 0 && !legacyRoutes) add(Action.REMOVE_FROM_RESUME)
        add(if (item.played) Action.MARK_UNPLAYED else Action.MARK_PLAYED)
        add(if (item.favorite) Action.FAVORITE_REMOVE else Action.FAVORITE_ADD)
        if (item.isEpisode && !item.seriesId.isNullOrBlank()) add(Action.GO_TO_SERIES)
    }

    @Volatile private var inFlight: String? = null

    /** Shows the menu; after a server action the item is reloaded and handed to [onChanged]. */
    fun show(activity: AppCompatActivity, scope: CoroutineScope, item: MediaItem, onChanged: (MediaItem) -> Unit) {
        if (inFlight == item.id) return
        val actions = entries(item, ServiceLocator.client.legacyRoutes())
        AlertDialog.Builder(activity).setTitle(item.name)
            .setItems(actions.map { activity.getString(it.label) }.toTypedArray()) { _, index ->
                when (val action = actions[index]) {
                    Action.GO_TO_SERIES -> DetailActivity.start(activity, item.seriesId!!)
                    else -> perform(activity, scope, item, action, onChanged)
                }
            }.show()
    }

    private fun perform(activity: AppCompatActivity, scope: CoroutineScope, item: MediaItem, action: Action, onChanged: (MediaItem) -> Unit) {
        inFlight = item.id
        scope.launch {
            try {
                val client = ServiceLocator.client
                when (action) {
                    Action.REMOVE_FROM_RESUME -> client.resetResume(item.id)
                    Action.MARK_PLAYED -> client.setPlayed(item.id, true)
                    Action.MARK_UNPLAYED -> client.setPlayed(item.id, false)
                    Action.FAVORITE_ADD -> client.setFavorite(item.id, true)
                    Action.FAVORITE_REMOVE -> client.setFavorite(item.id, false)
                    Action.GO_TO_SERIES -> Unit
                }
                onChanged(client.item(item.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(activity, activity.errorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                inFlight = null
            }
        }
    }
}
