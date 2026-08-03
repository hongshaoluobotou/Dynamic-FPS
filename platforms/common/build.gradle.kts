plugins {
	id("dynamic_fps.base")
	id("dynamic_fps.java")
	id("dynamic_fps.common")
	alias(libs.plugins.loom)
}

loom {
	accessWidenerPath = file("src/main/resources/dynamic_fps.accesswidener")
}

dependencies {
	minecraft(libs.minecraft)

	implementation(libs.battery)
	implementation(libs.cloth.config) { isTransitive = false }

	// Note: This is only here for the @Environment annotation, do not use!
	compileOnly(libs.fabric.loader)

	// Optional: SDL3 backend (BlazeSDL) shipped with Minecraft 26.1-26.2.
	// Used to bridge the SDL3 events that BlazeSDL does not route to the
	// GLFW callbacks Dynamic FPS relies on. Classes are loaded via
	// ServiceLoader at runtime; safe to omit.
	compileOnly(libs.blazesdl.api)
}
