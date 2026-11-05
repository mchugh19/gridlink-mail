package app.gridlink.core.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * One cached VTODO: a to-do out of a CalDAV calendar, the task counterpart of [CalendarEventEntity].
 *
 * The columns are the parse of [raw], written once at sync time, on the same terms as the event
 * table: [dueLocal] and [startLocal] are wall times in [zoneId], never instants, because a weekly
 * task is due on Thursday in the zone it was written in on both sides of a daylight-saving
 * change. [dueDay] is the range index, an epoch day in that same zone.
 *
 * Completed and cancelled rows are kept rather than deleted, for the event table's reason: the
 * server still lists them, so dropping one here would fetch it back on every sync. Readers
 * filter them out.
 */
@Entity(
    tableName = "calendar_tasks",
    primaryKeys = ["accountId", "href"],
    indices = [Index("accountId", "dueDay")],
)
data class CalendarTaskEntity(
    /** Local StoredAccount id owning this task. */
    val accountId: String,
    /** Path on the server, percent-decoded. Stable identity for the row. */
    val href: String,
    /** The [DavCollectionEntity.url] this came from, so one calendar can be cleared alone. */
    val collectionUrl: String,
    val etag: String?,
    /** iCalendar UID. Shared by a repeating task and its detached overrides, so NOT a key. */
    val uid: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    /** ISO local date-time the task is due, or null for a someday task with no DUE or DTSTART. */
    val dueLocal: String?,
    /** ISO local date-time of DTSTART, the recurrence anchor, or null when the file has none. */
    val startLocal: String?,
    /** IANA zone id the two above are written in. */
    val zoneId: String,
    /** The due was a plain date, so the task is due that day rather than at a time. */
    val allDay: Boolean,
    /** STATUS verbatim, upper case: NEEDS-ACTION, IN-PROCESS, COMPLETED or CANCELLED. */
    val status: String,
    /** ISO local date-time the task was completed, or null. */
    val completedLocal: String?,
    val percentComplete: Int?,
    val priority: Int?,
    /** The RRULE text, unparsed, or null for a one-off. */
    val rrule: String?,
    /** Cancelled instances, ISO dates joined by commas; empty when there are none. */
    val exDates: String,
    /** ISO date of the instance this row replaces, or null when this row is the master. */
    val recurrenceId: String?,
    /** Epoch day of [dueLocal], in [zoneId]; null when there is no due. */
    val dueDay: Long?,
    /** The `.ics` payload behind the columns, byte for byte as the CalDAV path received it. */
    val raw: String,
)
