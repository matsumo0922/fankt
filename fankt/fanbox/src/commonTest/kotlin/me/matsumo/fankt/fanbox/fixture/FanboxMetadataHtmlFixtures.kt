package me.matsumo.fankt.fanbox.fixture

internal object FanboxMetadataHtmlFixtures {
    val home =
        """
        <!doctype html>
        <html lang="fixture-lang">
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="metadata" content="{&quot;apiUrl&quot;:&quot;https://api.example.invalid&quot;,&quot;context&quot;:{&quot;privacyPolicy&quot;:{&quot;policyUrl&quot;:&quot;https://example.invalid/privacy&quot;,&quot;revisionHistoryUrl&quot;:&quot;https://example.invalid/privacy/history&quot;,&quot;shouldShowNotice&quot;:false,&quot;updateDate&quot;:&quot;2000-04-01&quot;},&quot;user&quot;:{&quot;creatorId&quot;:null,&quot;fanboxUserStatus&quot;:0,&quot;hasAdultContent&quot;:null,&quot;hasUnpaidPayments&quot;:false,&quot;iconUrl&quot;:null,&quot;isCreator&quot;:false,&quot;isMailAddressOutdated&quot;:false,&quot;isSupporter&quot;:false,&quot;lang&quot;:&quot;fixture-lang&quot;,&quot;name&quot;:&quot;Fixture Metadata User&quot;,&quot;planCount&quot;:0,&quot;showAdultContent&quot;:false,&quot;userId&quot;:&quot;93000001&quot;}},&quot;csrfToken&quot;:&quot;fixture-token&quot;}">
          </head>
          <body></body>
        </html>
        """.trimIndent()
}
