// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "XTAPES.IN - The Number One Porn Tube For Best 4K and Full HD Porn Videos. Watch, Download & Share XXX scenes and Movies every day for free! Featuring worlds best Pornstars & cam models of the Adult Industry."
    language    = "en"
    authors = listOf("HindiProviders")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("NSFW")
    iconUrl = "https://xtapes.in/wp-content/uploads/2025/05/cropped-xtapes-logo-transparent2-5-192x192.png"
}
