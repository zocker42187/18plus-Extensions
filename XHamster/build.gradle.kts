version = 1

cloudstream {
    authors     = listOf("specko")
    language    = "en"
    description = "XHamster"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("NSFW")
    iconUrl = "https://xhamster.com/favicon.ico"
}
