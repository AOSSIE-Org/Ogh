package com.ogh.shared.ui.navigation

/**
 * Navigation destinations for the Ogh app.
 *
 * Preview and Settings are the two root sections; configuration destinations
 * are nested under Settings and destination forms remain one level deeper.
 */
enum class Screen(val route: String, val title: String) {
    PREVIEW("preview", "Preview"),
    DESTINATIONS("destinations", "Destinations"),
    ADD_DESTINATION("add_destination", "Add Destination"),
    EDIT_DESTINATION("edit_destination", "Edit Destination"),
    SETTINGS("settings", "Settings"),
    ACCOUNTS("accounts", "Platforms"),
    ABOUT("about", "About"),
}
