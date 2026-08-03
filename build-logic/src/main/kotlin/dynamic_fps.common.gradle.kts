repositories {
	exclusiveContent {
		forRepository {
			maven {
				name = "Architectury"
				url = uri("https://maven.architectury.dev")
			}
		}
		filter {
			includeGroup("me.shedaniel.cloth")
		}
	}
	exclusiveContent {
		forRepository {
			maven {
				name = "LostLuma"
				url = uri("https://maven.lostluma.net/releases")
			}
		}
		filter {
			includeGroup("net.lostluma")
		}
	}
	exclusiveContent {
		forRepository {
			maven {
				name = "fifth_light"
				url = uri("https://maven.fifthlight.top/releases")
			}
		}
		filter {
			includeGroup("top.fifthlight.blazesdl")
		}
	}
}
