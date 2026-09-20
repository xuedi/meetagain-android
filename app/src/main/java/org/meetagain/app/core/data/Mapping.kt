package org.meetagain.app.core.data

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.meetagain.app.core.network.AttendeeListDto
import org.meetagain.app.core.network.CommentListDto
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventGroupDto
import org.meetagain.app.core.network.EventListDto
import org.meetagain.app.core.network.EventSummaryDto
import org.meetagain.app.core.network.ImageListDto
import org.meetagain.app.core.network.InvitationListDto
import org.meetagain.app.core.network.MeDto
import org.meetagain.app.core.network.MembershipDto
import org.meetagain.app.core.network.MembershipListDto
import org.meetagain.app.core.network.NotificationListDto
import org.meetagain.app.core.network.NotificationSettingsChangeDto
import org.meetagain.app.core.network.NotificationSettingsDto

/**
 * The server's answers as the app's models. Only images on the app's own server are kept, so content can never make
 * the app contact anyone else.
 */
class ImageHost(private val host: String) {
    fun own(url: String?): String? = url?.toHttpUrlOrNull()?.takeIf { it.host == host }?.toString()
}

fun EventListDto.toUpcoming(images: ImageHost, clock: Clock): Upcoming {
    val now = clock.instant()
    val events = items.mapNotNull { it.toEvent(images) }.filter { it.calendarEnd.isAfter(now) }
    return Upcoming(events, complete = items.size >= total)
}

fun EventSummaryDto.toEvent(images: ImageHost): Event? = eventOf(
    id = id,
    title = title,
    teaser = teaser,
    start = start,
    stop = stop,
    type = type,
    going = rsvpCount,
    attending = attendeeCount,
    canceled = canceled,
    seriesId = seriesId,
    group = group,
    myRsvp = myRsvp,
    myGuests = myGuests,
    imageUrl = previewImageUrl,
    webUrl = webUrl,
    images = images
)

fun EventDetailDto.toDetails(images: ImageHost): EventDetails? {
    val event = eventOf(
        id = id,
        title = title,
        teaser = teaser,
        start = start,
        stop = stop,
        type = type,
        going = rsvpCount,
        attending = attendeeCount,
        canceled = canceled,
        seriesId = seriesId,
        group = group,
        myRsvp = myRsvp,
        myGuests = myGuests,
        imageUrl = previewImageUrl,
        webUrl = webUrl,
        images = images
    ) ?: return null
    return EventDetails(
        event = event,
        description = description?.takeIf { it.isNotBlank() },
        location = location
            ?.let { Location(it.name.orNull(), it.street.orNull(), it.postcode.orNull(), it.city.orNull()) }
            ?.takeIf { it.query.isNotEmpty() },
        photoUrls = this.images.mapNotNull { images.own(it) }
    )
}

fun AttendeeListDto.toAttendees(images: ImageHost) = Attendees(
    people = items.map { Attendee(it.id, it.name, images.own(it.avatarUrl), it.guests, it.mine) },
    externalCount = externalCount,
    total = total
)

fun CommentListDto.toConversation(images: ImageHost) = Conversation(
    comments = items.map {
        Comment(
            id = it.id,
            authorName = it.author.name,
            authorAvatarUrl = images.own(it.author.avatarUrl),
            writtenAt = it.createdAt?.let(::instant),
            text = it.content,
            mine = it.mine,
            canDelete = it.canDelete
        )
    },
    total = total,
    olderBefore = nextBefore
)

/** The grid shows the smallest generated size and the full view the largest. */
fun ImageListDto.toPhotos(images: ImageHost): List<Photo> = items.mapNotNull { image ->
    val full = images.own(image.url) ?: return@mapNotNull null
    val thumbnail = image.urls[GRID_SIZE]?.let(images::own) ?: full
    Photo(image.id, full, thumbnail, image.mine)
}

fun MembershipListDto.toMemberships(images: ImageHost) = items.map { it.toMembership(images) }

fun MembershipDto.toMembership(images: ImageHost) = Membership(
    group = Group(group.slug, group.name, images.own(group.logoUrl)),
    role = role,
    status = when (status) {
        "approved" -> MembershipStatus.Approved
        "rejected" -> MembershipStatus.Rejected
        else -> MembershipStatus.Pending
    },
    blocked = blocked,
    joinedAt = joinedAt?.let(::instant)
)

fun InvitationListDto.toInvitations(images: ImageHost) = items.map {
    Invitation(
        id = it.id,
        group = Group(it.group.slug, it.group.name, images.own(it.group.logoUrl)),
        role = it.role,
        invitedBy = it.invitedBy.orNull(),
        expiresAt = it.expiresAt?.let(::instant)
    )
}

fun MeDto.toProfile(images: ImageHost) = Profile(
    id = id,
    name = name,
    email = email,
    bio = bio.orNull(),
    language = locale,
    public = public,
    avatarUrl = images.own(avatarUrl)
)

/** Null for an event without a readable start, which the app has nothing to show for. */
@Suppress("LongParameterList")
private fun eventOf(
    id: Int,
    title: String,
    teaser: String?,
    start: String,
    stop: String?,
    type: Int?,
    going: Int,
    attending: Int,
    canceled: Boolean,
    seriesId: Int?,
    group: EventGroupDto?,
    myRsvp: Boolean?,
    myGuests: Int?,
    imageUrl: String?,
    webUrl: String,
    images: ImageHost
): Event? {
    val startsAt = instant(start) ?: return null
    return Event(
        id = id,
        title = title,
        teaser = teaser.orNull(),
        start = startsAt,
        end = stop?.let(::instant),
        kind = when (type) {
            OUTDOOR -> EventKind.Outdoor
            DINNER -> EventKind.Dinner
            else -> null
        },
        going = going,
        attending = attending,
        canceled = canceled,
        seriesId = seriesId,
        group = group?.let { Group(it.slug, it.name, images.own(it.logoUrl)) },
        mine = myRsvp?.let { Rsvp(it, myGuests ?: 0) },
        imageUrl = images.own(imageUrl),
        webUrl = webUrl
    )
}

internal fun instant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeParseException) {
    null
}

internal fun String?.orNull() = this?.trim()?.takeIf { it.isNotEmpty() }

private const val GRID_SIZE = "350x263"
private const val OUTDOOR = 3
private const val DINNER = 4

/** An item whose label is blank says nothing worth a row, so it is left out rather than shown empty. */
internal fun NotificationListDto.toNotifications(): List<Notification> = items.mapNotNull { item ->
    item.label.orNull()?.let { Notification(key = item.key, text = it, webUrl = item.webUrl.orNull()) }
}

internal fun NotificationSettingsDto.toSettings() = NotificationSettings(
    master = enabled,
    announcements = announcements,
    followingUpdates = followingUpdates,
    receivedMessage = receivedMessage,
    eventReminder = eventReminder,
    upcomingEvents = upcomingEvents,
    attendedEventUpdate = attendedEventUpdate,
    other = OtherSettings(
        push = push,
        quietHours = quietHours?.let {
            QuietHours(it.enabled, it.start, it.end, it.timeZone, it.allowUrgent)
        }
    )
)

/** One switch as the server takes it: everything else stays absent, so the server keeps what it has stored. */
internal fun NotificationSetting.change(value: Boolean): NotificationSettingsChangeDto = when (this) {
    NotificationSetting.Master -> NotificationSettingsChangeDto(enabled = value)
    NotificationSetting.Announcements -> NotificationSettingsChangeDto(announcements = value)
    NotificationSetting.FollowingUpdates -> NotificationSettingsChangeDto(followingUpdates = value)
    NotificationSetting.ReceivedMessage -> NotificationSettingsChangeDto(receivedMessage = value)
    NotificationSetting.EventReminder -> NotificationSettingsChangeDto(eventReminder = value)
    NotificationSetting.UpcomingEvents -> NotificationSettingsChangeDto(upcomingEvents = value)
    NotificationSetting.AttendedEventUpdate -> NotificationSettingsChangeDto(attendedEventUpdate = value)
}
