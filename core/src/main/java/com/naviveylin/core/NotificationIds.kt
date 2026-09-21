package com.naviveylin.core

/**
 * The app's notification identities (spec: navigation-ongoing-notification — Distinct
 * notification identity).
 *
 * The platform keys a posted notification by (tag, id) and the app posts these with a null
 * tag from one process, so the ids must not collide. The collision is not cosmetic: the
 * ongoing navigation notification is the foreground-service notification **and** the carrier
 * of the car rail-widget turn hint (its `CarAppExtender`), so a notice posted on its id takes
 * that identity over — a map-style failure notice did exactly that, per screen start.
 *
 * One source, with the uniqueness asserted by `NotificationIdsTest`.
 */
object NotificationIds {

    /** Map-download progress and completion (`MapDownloadService`). */
    const val MAP_DOWNLOAD = 1001

    /**
     * Ongoing navigation / free-driving notification. Also the foreground-service
     * notification of `NavigationNotificationService` and the carrier of the car
     * rail-widget turn hint.
     */
    const val NAVIGATION_ONGOING = 1002

    /** Car map-style load-failure notice (`CarStyleLoadNotifier`). */
    const val MAP_STYLE_NOTICE = 1003
}
