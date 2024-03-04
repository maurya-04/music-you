package it.vfsfitvnm.vimusic.ui.components.themed

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.enums.PlaylistSortBy
import it.vfsfitvnm.vimusic.enums.SortOrder
import it.vfsfitvnm.vimusic.models.Info
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.items.MediaSongItem
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.utils.addNext
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.enqueue
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.formatAsDuration
import it.vfsfitvnm.vimusic.utils.playlistSortByKey
import it.vfsfitvnm.vimusic.utils.playlistSortOrderKey
import it.vfsfitvnm.vimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@ExperimentalAnimationApi
@Composable
fun InHistoryMediaItemMenu(
    onDismiss: () -> Unit,
    song: Song,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current

    var isHiding by remember {
        mutableStateOf(false)
    }

    if (isHiding) {
        ConfirmationDialog(
            title = stringResource(id = R.string.hide_song_dialog),
            text = stringResource(id = R.string.hide_song_dialog_description),
            onDismiss = { isHiding = false },
            onConfirm = {
                onDismiss()
                query {
                    // Not sure we can do this here
                    binder?.cache?.removeResource(song.id)
                    Database.incrementTotalPlayTimeMs(song.id, -song.totalPlayTimeMs)
                }
            }
        )
    }

    NonQueuedMediaItemMenu(
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onHideFromDatabase = { isHiding = true },
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun InPlaylistMediaItemMenu(
    onDismiss: () -> Unit,
    playlistId: Long,
    positionInPlaylist: Int,
    song: Song,
    modifier: Modifier = Modifier
) {
    NonQueuedMediaItemMenu(
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onRemoveFromPlaylist = {
            transaction {
                Database.move(playlistId, positionInPlaylist, Int.MAX_VALUE)
                Database.delete(SongPlaylistMap(song.id, playlistId, Int.MAX_VALUE))
            }
        },
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun NonQueuedMediaItemMenu(
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
) {
    val binder = LocalPlayerServiceBinder.current

    BaseMediaItemMenu(
        onDismiss = onDismiss,
        mediaItem = mediaItem,
        modifier = modifier,
        onStartRadio = {
            binder?.stopRadio()
            binder?.player?.forcePlay(mediaItem)
            binder?.setupRadio(
                NavigationEndpoint.Endpoint.Watch(
                    videoId = mediaItem.mediaId,
                    playlistId = mediaItem.mediaMetadata.extras?.getString("playlistId")
                )
            )
        },
        onPlayNext = { binder?.player?.addNext(mediaItem) },
        onEnqueue = { binder?.player?.enqueue(mediaItem) },
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onHideFromDatabase = onHideFromDatabase,
        onRemoveFromQuickPicks = onRemoveFromQuickPicks
    )
}

@ExperimentalAnimationApi
@Composable
fun QueuedMediaItemMenu(
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    indexInQueue: Int?,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current

    BaseMediaItemMenu(
        onDismiss = onDismiss,
        mediaItem = mediaItem,
        modifier = modifier,
        onRemoveFromQueue = if (indexInQueue != null) ({
            binder?.player?.removeMediaItem(indexInQueue)
        }) else null
    )
}

@ExperimentalAnimationApi
@Composable
fun BaseMediaItemMenu(
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onShowSleepTimer: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    MediaItemMenu(
        onDismiss = onDismiss,
        mediaItem = mediaItem,
        modifier = modifier,
        onShowSleepTimer = onShowSleepTimer,
        onStartRadio = onStartRadio,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onHideFromDatabase = onHideFromDatabase,
        onRemoveFromQueue = onRemoveFromQueue,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onAddToPlaylist = { playlist, position ->
            transaction {
                Database.insert(mediaItem)
                Database.insert(
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = Database.insert(playlist).takeIf { it != -1L } ?: playlist.id,
                        position = position
                    )
                )
            }
        },
        onGoToAlbum = albumRoute::global,
        onGoToArtist = artistRoute::global,
        onRemoveFromQuickPicks = onRemoveFromQuickPicks
    ) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "https://music.youtube.com/watch?v=${mediaItem.mediaId}"
            )
        }

        context.startActivity(Intent.createChooser(sendIntent, null))
    }
}

@ExperimentalAnimationApi
@Composable
fun MediaItemMenu(
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onShowSleepTimer: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist, Int) -> Unit)? = null,
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    val density = LocalDensity.current

    var isViewingPlaylists by remember {
        mutableStateOf(false)
    }

    var height by remember {
        mutableStateOf(0.dp)
    }

    var albumInfo by remember {
        mutableStateOf(mediaItem.mediaMetadata.extras?.getString("albumId")?.let { albumId ->
            Info(albumId, null)
        })
    }

    var artistsInfo by remember {
        mutableStateOf(
            mediaItem.mediaMetadata.extras?.getStringArrayList("artistNames")?.let { artistNames ->
                mediaItem.mediaMetadata.extras?.getStringArrayList("artistIds")?.let { artistIds ->
                    artistNames.zip(artistIds).map { (authorName, authorId) ->
                        Info(authorId, authorName)
                    }
                }
            }
        )
    }

    var likedAt by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (albumInfo == null) albumInfo = Database.songAlbumInfo(mediaItem.mediaId)
            if (artistsInfo == null) artistsInfo = Database.songArtistInfo(mediaItem.mediaId)

            Database.likedAt(mediaItem.mediaId).collect { likedAt = it }
        }
    }

    AnimatedContent(
        targetState = isViewingPlaylists,
        transitionSpec = {
            val animationSpec = tween<IntOffset>(400)
            val slideDirection =
                if (targetState) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right

            slideIntoContainer(slideDirection, animationSpec) togetherWith
                    slideOutOfContainer(slideDirection, animationSpec)
        },
        label = ""
    ) { currentIsViewingPlaylists ->
        if (currentIsViewingPlaylists) {
            val sortBy by rememberPreference(playlistSortByKey, PlaylistSortBy.DateAdded)
            val sortOrder by rememberPreference(playlistSortOrderKey, SortOrder.Descending)

            val playlistPreviews by remember {
                Database.playlistPreviews(sortBy, sortOrder)
            }.collectAsState(initial = emptyList(), context = Dispatchers.IO)

            var isCreatingNewPlaylist by rememberSaveable {
                mutableStateOf(false)
            }

            if (isCreatingNewPlaylist && onAddToPlaylist != null) {
                TextFieldDialog(
                    title = stringResource(id = R.string.new_playlist),
                    hintText = stringResource(id = R.string.playlist_name_hint),
                    onDismiss = { isCreatingNewPlaylist = false },
                    onDone = { text ->
                        onDismiss()
                        onAddToPlaylist(Playlist(name = text), 0)
                    }
                )
            }

            BackHandler {
                isViewingPlaylists = false
            }

            Menu(
                modifier = modifier
                    .requiredHeight(height)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { isViewingPlaylists = false }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }

                    if (onAddToPlaylist != null) {
                        FilledTonalButton(
                            onClick = { isCreatingNewPlaylist = true },
                            enabled = true
                        ) {
                            Text(
                                text = stringResource(id = R.string.new_playlist)
                            )
                        }
                    }
                }

                onAddToPlaylist?.let { onAddToPlaylist ->
                    playlistPreviews.forEach { playlistPreview ->
                        MenuEntry(
                            icon = Icons.AutoMirrored.Outlined.QueueMusic,
                            text = playlistPreview.playlist.name,
                            secondaryText =
                            if (playlistPreview.songCount == 1) "1 ${stringResource(id = R.string.song).lowercase()}"
                            else "${playlistPreview.songCount} ${stringResource(id = R.string.songs).lowercase()}",
                            onClick = {
                                onDismiss()
                                onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                            }
                        )
                    }
                }
            }
        } else {
            Menu(
                modifier = modifier
                    .onPlaced { height = with(density) { it.size.height.toDp() } }
            ) {
                MediaSongItem(
                    song = mediaItem,
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    query {
                                        if (Database.like(
                                                mediaItem.mediaId,
                                                if (likedAt == null) System.currentTimeMillis() else null
                                            ) == 0
                                        ) {
                                            Database.insert(mediaItem, Song::toggleLike)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (likedAt == null) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            IconButton(
                                onClick = onShare
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                onStartRadio?.let { onStartRadio ->
                    MenuEntry(
                        icon = Icons.Outlined.Podcasts,
                        text = stringResource(id = R.string.start_radio),
                        onClick = {
                            onDismiss()
                            onStartRadio()
                        }
                    )
                }

                onPlayNext?.let { onPlayNext ->
                    MenuEntry(
                        icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                        text = stringResource(id = R.string.play_next),
                        onClick = {
                            onDismiss()
                            onPlayNext()
                        }
                    )
                }

                onEnqueue?.let { onEnqueue ->
                    MenuEntry(
                        icon = Icons.AutoMirrored.Outlined.QueueMusic,
                        text = stringResource(id = R.string.enqueue),
                        onClick = {
                            onDismiss()
                            onEnqueue()
                        }
                    )
                }

                // TODO: find solution to this
                onShowSleepTimer?.let {
                    val binder = LocalPlayerServiceBinder.current

                    var isShowingSleepTimerDialog by remember {
                        mutableStateOf(false)
                    }

                    val sleepTimerMillisLeft by (binder?.sleepTimerMillisLeft
                        ?: flowOf(null))
                        .collectAsState(initial = null)

                    if (isShowingSleepTimerDialog) {
                        if (sleepTimerMillisLeft != null) {
                            ConfirmationDialog(
                                title = stringResource(id = R.string.stop_sleep_timer_dialog),
                                onDismiss = { isShowingSleepTimerDialog = false },
                                onConfirm = {
                                    binder?.cancelSleepTimer()
                                    onDismiss()
                                },
                                cancelText = stringResource(id = R.string.no),
                                confirmText = stringResource(id = R.string.yes)
                            )
                        } else {
                            var amount by remember {
                                mutableIntStateOf(1)
                            }

                            AlertDialog(
                                onDismissRequest = { isShowingSleepTimerDialog = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            binder?.startSleepTimer(amount * 10 * 60 * 1000L)
                                            isShowingSleepTimerDialog = false
                                        },
                                        enabled = amount > 0
                                    ) {
                                        Text(text = stringResource(id = R.string.set))
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { isShowingSleepTimerDialog = false }
                                    ) {
                                        Text(text = stringResource(id = R.string.cancel))
                                    }
                                },
                                title = {
                                    Text(text = stringResource(id = R.string.set_sleep_timer))
                                },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(
                                            space = 16.dp,
                                            alignment = Alignment.CenterHorizontally
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { amount-- },
                                            enabled = amount > 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Remove,
                                                contentDescription = null
                                            )
                                        }

                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${amount / 6}h ${(amount % 6) * 10}m",
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = { amount++ },
                                            enabled = amount < 60
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    MenuEntry(
                        icon = Icons.Outlined.Alarm,
                        text = stringResource(id = R.string.sleep_timer),
                        onClick = { isShowingSleepTimerDialog = true },
                        trailingContent = sleepTimerMillisLeft?.let {
                            {
                                Text(
                                    text = "${formatAsDuration(it)} left",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = modifier
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .animateContentSize()
                                )
                            }
                        }
                    )
                }

                if (onAddToPlaylist != null) {
                    MenuEntry(
                        icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        text = stringResource(id = R.string.add_to_playlist),
                        onClick = { isViewingPlaylists = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null
                            )
                        }
                    )
                }

                onGoToAlbum?.let { onGoToAlbum ->
                    albumInfo?.let { (albumId) ->
                        MenuEntry(
                            icon = Icons.Outlined.Album,
                            text = stringResource(id = R.string.go_to_album),
                            onClick = {
                                onDismiss()
                                onGoToAlbum(albumId)
                            }
                        )
                    }
                }

                onGoToArtist?.let { onGoToArtist ->
                    artistsInfo?.forEach { (authorId, authorName) ->
                        MenuEntry(
                            icon = Icons.Outlined.Person,
                            text = stringResource(id = R.string.more_from) + " $authorName",
                            onClick = {
                                onDismiss()
                                onGoToArtist(authorId)
                            }
                        )
                    }
                }

                onRemoveFromQueue?.let { onRemoveFromQueue ->
                    MenuEntry(
                        icon = Icons.Outlined.PlaylistRemove,
                        text = stringResource(id = R.string.remove_from_queue),
                        onClick = {
                            onDismiss()
                            onRemoveFromQueue()
                        }
                    )
                }

                onRemoveFromPlaylist?.let { onRemoveFromPlaylist ->
                    MenuEntry(
                        icon = Icons.Outlined.PlaylistRemove,
                        text = stringResource(id = R.string.remove_from_playlist),
                        onClick = {
                            onDismiss()
                            onRemoveFromPlaylist()
                        }
                    )
                }

                onHideFromDatabase?.let { onHideFromDatabase ->
                    MenuEntry(
                        icon = Icons.Outlined.Delete,
                        text = stringResource(id = R.string.hide),
                        onClick = onHideFromDatabase
                    )
                }

                onRemoveFromQuickPicks?.let {
                    MenuEntry(
                        icon = Icons.Outlined.Delete,
                        text = stringResource(id = R.string.hide_from_quick_picks),
                        onClick = {
                            onDismiss()
                            onRemoveFromQuickPicks()
                        }
                    )
                }
            }
        }
    }
}