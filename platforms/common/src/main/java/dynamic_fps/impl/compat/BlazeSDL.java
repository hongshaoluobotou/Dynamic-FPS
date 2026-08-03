package dynamic_fps.impl.compat;

import dynamic_fps.impl.Constants;
import dynamic_fps.impl.feature.state.WindowObserver;
import dynamic_fps.impl.util.Logging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLVideo;
import top.fifthlight.blazesdl.api.BlazeSDLAPI;
import top.fifthlight.blazesdl.api.BlazeSDLEventHandler;

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
 * {@link BlazeSDLEventHandler} through BlazeSDL's public SPI. The handler
 * forwards SDL3 events to the same {@code WindowObserver} state machine used
 * for the GLFW path, so the rest of the mod does not need to special-case the
 * backend.
 *
 * <p>On Windows, SDL3 does not always pair {@code SDL_EVENT_WINDOW_MINIMIZED}
 * with a {@code SDL_EVENT_WINDOW_FOCUS_LOST} event (this depends on how the
 * window was minimized — taskbar right-click and certain Win+ shortcuts can
 * skip the focus-loss path). GLFW, in contrast, guarantees that a minimized
 * window reports as not focused and not hovered, regardless of the underlying
 * WndProc behavior. To match the GLFW path, the {@link Bridge} also clears
 * the focus and hover state when handling a minimize event, mirroring GLFW's
 * own bookkeeping.
 */
public final class BlazeSDL {
	private BlazeSDL() {
	}

	/**
	 * Whether Minecraft is currently using the BlazeSDL (SDL3) backend.
	 *
	 * <p>When {@code false} the GLFW path is used and this class is a no-op.
	 *
	 * <p>The BlazeSDL API is loaded via {@link ServiceLoader} (and any
	 * {@link LinkageError} is swallowed) so this class is safe to reference
	 * when Dynamic FPS runs against a vanilla GLFW setup that does not have
	 * the BlazeSDL API jar on the classpath.
	 */
	public static boolean isActive() {
		return api() != null;
	}

	private static @Nullable BlazeSDLAPI api() {
		try {
			return BlazeSDLAPI.getInstance();
		} catch (LinkageError ignored) {
			// BlazeSDL API not on the classpath (vanilla GLFW setup).
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
		BlazeSDLAPI api = api();
		if (api == null) {
			return;
		}

		long flags = SDLVideo.SDL_GetWindowFlags(handle);
		observer.setInitialState(
			(flags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0,
			(flags & SDLVideo.SDL_WINDOW_MINIMIZED) != 0,
			(flags & SDLVideo.SDL_WINDOW_MOUSE_FOCUS) != 0
		);

		api.registerEventHandler(new Bridge(observer, handle));
	}

	private static final class Bridge implements BlazeSDLEventHandler {
		private final WindowObserver observer;
		private final long handle;

		Bridge(WindowObserver observer, long handle) {
			this.observer = observer;
			this.handle = handle;
		}

		@Override
		public int getPriority() {
			return 0;
		}

		@Override
		public boolean handleEvent(@NonNull SDL_Event event) {
			int type = event.type();

			switch (type) {
				case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED -> {
					observer.invokeFocus(this.handle, true);
				}
				case SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST -> {
					observer.invokeFocus(this.handle, false);
				}
				case SDLEvents.SDL_EVENT_WINDOW_MINIMIZED -> {
					observer.invokeIconify(this.handle, true);
					// On Windows, SDL3 does not always pair MINIMIZED with
					// FOCUS_LOST (depends on how the window was minimized).
					// A minimized window physically cannot have input focus
					// or mouse hover, so force-clear them here to match the
					// GLFW path's bookkeeping. Without this, a stale
					// isFocused=true would mask the INVISIBLE state in
					// DynamicFPSMod.checkForStateChanges0.
					observer.invokeFocus(this.handle, false);
					observer.invokeCursorEnter(this.handle, false);
				}
				case SDLEvents.SDL_EVENT_WINDOW_RESTORED -> {
					observer.invokeIconify(this.handle, false);
				}
				case SDLEvents.SDL_EVENT_WINDOW_MOUSE_ENTER -> {
					observer.invokeCursorEnter(this.handle, true);
				}
				case SDLEvents.SDL_EVENT_WINDOW_MOUSE_LEAVE -> {
					observer.invokeCursorEnter(this.handle, false);
				}
				default -> {
					return false;
				}
			}

			if (Constants.DEBUG) {
				Logging.getLogger().debug("[BlazeSDL] Forwarded SDL event 0x{} to WindowObserver", Integer.toHexString(type));
			}

			// Do not consume the event — BlazeSDL's own RenderSystemMixin switch
			// also dispatches the focus event to Minecraft's Window::onFocus,
			// and the rest have no consumer; leaving them unconsumed keeps us
			// well-behaved for any other consumer.
			return false;
		}
	}
}
