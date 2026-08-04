package dynamic_fps.impl.compat;

import dynamic_fps.impl.Constants;
import dynamic_fps.impl.feature.state.WindowObserver;
import dynamic_fps.impl.util.Logging;
import org.jspecify.annotations.NonNull;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import top.fifthlight.blazesdl.api.BlazeSDLEventHandler;

/**
 * SDL3 event handler that forwards the events Minecraft does not deliver
 * to Dynamic FPS's {@link WindowObserver} when running under the BlazeSDL
 * (SDL3) backend. Lives in its own top-level class so that loading
 * {@link BlazeSDL} does not force resolution of {@link BlazeSDLEventHandler}
 * when the BlazeSDL API is absent from the classpath (e.g. a setup that
 * only has TouchController installed, without the main BlazeSDL mod).
 *
 * <p>Only instantiate this class after {@link BlazeSDL#isActive()} returns
 * {@code true}; otherwise the BlazeSDL SPI is not on the classpath and the
 * {@link BlazeSDLEventHandler} interface cannot be linked.
 */
final class BlazeSDLEventBridge implements BlazeSDLEventHandler {
	private final WindowObserver observer;
	private final long handle;

	BlazeSDLEventBridge(WindowObserver observer, long handle) {
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
				// FOCUS_LOST (depends on how the window was minimized). A
				// minimized window physically cannot have input focus or
				// mouse hover, so force-clear them here to match the GLFW
				// path's bookkeeping. Without this, a stale isFocused=true
				// would mask the INVISIBLE state in
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
		// also dispatches the focus event to Minecraft's Window::onFocus, and
		// the rest have no consumer; leaving them unconsumed keeps us
		// well-behaved for any other consumer.
		return false;
	}
}
