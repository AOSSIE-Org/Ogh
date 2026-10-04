package com.ogh.shared.ui.navigation

/**
 * Navigation destinations for the Ogh app.
 *
 * Stream, Settings, and About are the primary root sections accessible via
 * the navigation bar; configuration destinations are nested under Settings.
 */
enum class Screen(val route: String, val title: String) {
    PREVIEW("preview", "Stream"),
    DESTINATIONS("destinations", "Destinations"),
    ADD_DESTINATION("add_destination", "Add Destination"),
    EDIT_DESTINATION("edit_destination", "Edit Destination"),
    SETTINGS("settings", "Settings"),
    ACCOUNTS("accounts", "Platforms"),
    ABOUT("about", "About"),
}
