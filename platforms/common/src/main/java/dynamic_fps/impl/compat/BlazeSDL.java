package dynamic_fps.impl.compat;

import dynamic_fps.impl.feature.state.WindowObserver;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLVideo;

/**
 * Compatibility shim for the BlazeSDL (SDL3) backend shipped with Minecraft 26.2+.
 *
 * <p>BlazeSDL intercepts Minecraft's GLFW callbacks and re-dispatches them from
 * its own event loop. Several events relevant to Dynamic FPS are
 * <em>not</em> routed through GLFW:
 * <ul>
 *   <li>{@code SDL_EVENT_WINDOW_MINIMIZED} / {@code SDL_EVENT_WINDOW_RESTORED} —
 *       would correspond to the {@code GLFWWindowIconifyCallback}.</li>
 *   <li>{@code SDL_EVENT_WINDOW_MOUSE_ENTER} / {@code SDL_EVENT_WINDOW_MOUSE_LEAVE} —
 *       would correspond to the {@code GLFWCursorEnterCallback}.</li>
 *   <li>Any callbacks registered with {@code glfwSetXxxCallback} <em>after</em>
 *       {@code Window.<init>} returns are silently dropped by BlazeSDL, because
 *       its event loop only invokes the static references it captured during
 *       the window constructor. In particular, focus changes are dispatched
 *       only to Minecraft's {@code Window::onFocus} and never reach the
 *       callback Dynamic FPS installs in {@link WindowObserver}.</li>
 * </ul>
 * As a result, {@link WindowObserver} never observes focus, iconify, or hover
 * changes when running under BlazeSDL, causing the mod to think the window is
 * still focused after it has been minimized, full-screened, or sent to the
 * background.
 *
 * <p>This class bridges those missing events by registering a
 * {@link BlazeSDLEventBridge} through BlazeSDL's public SPI. The handler
 * forwards SDL3 events to the same {@code WindowObserver} state machine used
 * for the GLFW path, so the rest of the mod does not need to special-case the
 * backend.
 *
 * <p>All access to the {@code top.fifthlight.blazesdl.api} package is
 * reflective so that loading this class never forces the JVM to resolve
 * {@code BlazeSDLEventHandler}. This is required because setups that ship
 * TouchController without the main BlazeSDL mod (FCL+Android, for example)
 * do not have the BlazeSDL API jar on the classpath; without this isolation
 * such setups would fail to launch Dynamic FPS with
 * {@code NoClassDefFoundError: BlazeSDLEventHandler}.
 */
public final class BlazeSDL {
	private BlazeSDL() {
	}

	/**
	 * Whether Minecraft is currently using the BlazeSDL (SDL3) backend.
	 *
	 * <p>When {@code false} the GLFW path is used and this class is a no-op.
	 */
	public static boolean isActive() {
		return api() != null;
	}

	/**
	 * Returns the {@code BlazeSDLAPI} instance, or {@code null} if the BlazeSDL
	 * API is not on the classpath. The return type is {@link Object} so that
	 * the class constant for {@code top.fifthlight.blazesdl.api.BlazeSDLAPI}
	 * never appears in this class's constant pool — referencing it directly
	 * would force the JVM to verify it at class-load time, which would fail
	 * with {@code NoClassDefFoundError} on any setup that does not have the
	 * API jar installed.
	 */
	private static @Nullable Object api() {
		try {
			Class<?> apiClass = Class.forName("top.fifthlight.blazesdl.api.BlazeSDLAPI", false, BlazeSDL.class.getClassLoader());
			return apiClass.getMethod("getInstance").invoke(null);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// BlazeSDL API not on the classpath (vanilla GLFW setup, or a
			// setup that only ships TouchController without the BlazeSDL
			// mod). Fall back to the GLFW path.
			return null;
		}
	}

	/**
	 * Wire SDL3 events to the given observer when running under BlazeSDL.
	 *
	 * <p>Also seeds the observer's initial focus/iconify/hover state from
	 * {@code SDL_GetWindowFlags} so the mod does not have to call the
	 * (unimplemented) {@code glfwGetWindowAttrib} for the SDL3 window.
	 */
	public static synchronized void initIfPresent(WindowObserver observer, long handle) {
		Object api = api();
		if (api == null) {
			return;
		}

		long flags = SDLVideo.SDL_GetWindowFlags(handle);
		observer.setInitialState(
			(flags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0,
			(flags & SDLVideo.SDL_WINDOW_MINIMIZED) != 0,
			(flags & SDLVideo.SDL_WINDOW_MOUSE_FOCUS) != 0
		);

		try {
			// The bridge is loaded reflectively so that the constant pool of
			// BlazeSDL.class never references dynamic_fps.impl.compat.BlazeSDLEventBridge
			// (which implements BlazeSDLEventHandler). Referencing the bridge
			// statically would force the JVM to resolve BlazeSDLEventHandler
			// at class-load time, breaking setups that only ship TouchController.
			Class<?> bridgeClass = Class.forName("dynamic_fps.impl.compat.BlazeSDLEventBridge");
			Object bridge = bridgeClass.getDeclaredConstructor(WindowObserver.class, long.class).newInstance(observer, handle);
			api.getClass().getMethod("registerEventHandler", Class.forName("top.fifthlight.blazesdl.api.BlazeSDLEventHandler")).invoke(api, bridge);
		} catch (ReflectiveOperationException | LinkageError e) {
			// BlazeSDL API is on the classpath but the bridge can't be
			// loaded. Give up — the GLFW path is no longer viable since
			// glfwGetWindowAttrib returns garbage under BlazeSDL, but at
			// least we won't crash the game.
		}
	}
}
