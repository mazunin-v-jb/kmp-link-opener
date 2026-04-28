package dev.hackathon.linkopener.platform

fun interface UrlReceiver {
    fun start(onUrl: (String) -> Unit)
}
