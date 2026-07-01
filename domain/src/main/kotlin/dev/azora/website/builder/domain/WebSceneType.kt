package dev.azora.website.builder.domain

/** The `.azn` scene `type` discriminator values owned by the website builder. */
object WebSceneType {
    const val PAGE = "azora-website-page"
    const val COMPONENT = "azora-website-component"
    const val NAVIGATION = "azora-website-navigation"
    const val CONFIG = "azora-website-config"

    val all = setOf(PAGE, COMPONENT, NAVIGATION, CONFIG)
}
