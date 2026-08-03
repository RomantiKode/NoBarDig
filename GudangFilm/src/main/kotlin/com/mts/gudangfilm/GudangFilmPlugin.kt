package com.mts.gudangfilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GudangFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GudangFilmProvider())

        listOf(
            CdnAmpprojectOrg(),
            Poker88PlayMe(),
            MorenciusCom(),
            Server3VipBcdnNet(),
            PremicloudNet(),
            VidhidehubCom(),
            VidhideplusCom(),
            BestxStream(),
            VidhideproCom(),
            PlaycinematicCom(),
            EmbedpyroxXyz(),
            AbyssplayerCom(),
            RpmPlayShare(),
            Embed4MePlay(),
            GoogleVideo(),
        ).forEach(::registerExtractorAPI)
    }
}
