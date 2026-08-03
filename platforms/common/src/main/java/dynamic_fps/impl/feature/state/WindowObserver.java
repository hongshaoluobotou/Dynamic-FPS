package dynamic_fps.impl.feature.state;

import dynamic_fps.impl.DynamicFPSMod;
import dynamic_fps.impl.compat.BlazeSDL;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.glfw.GLFWWindowIconifyCallback;

public class WindowObserver {
	private final long address;

	private boolean isFocused;
	private final GLFWWindowFocusCallback previousFocusCallback;

	private boolean isHovered;
	private final GLFWCursorEnterCallback previousMouseCallback;

	private boolean isIconified;
	private final GLFWWindowIconifyCallback previousIconifyCallback;

	public WindowObserver(long address) {
		this.address = address;

		// Under the BlazeSDL (SDL3) backend, glfwGetWindowAttrib is not
		// implemented for the SDL3 window and returns garbage. Defer the
		// initial state to the BlazeSDL bridge, which queries SDL_GetWindowFlags
		// directly. For the GLFW path, read the initial state from GLFW.
		boolean useBlazeSDLInitialState = BlazeSDL.isActive();
		this.isFocused = useBlazeSDLInitialState ? false : GLFW.glfwGetWindowAttrib(this.address, GLFW.GLFW_FOCUSED) != 0;
		this.previousFocusCallback = GLFW.glfwSetWindowFocusCallback(this.address, this::onFocusChanged);

		this.isHovered = useBlazeSDLInitialState ? false : GLFW.glfwGetWindowAttrib(this.address, GLFW.GLFW_HOVERED) != 0;
		this.previousMouseCallback = GLFW.glfwSetCursorEnterCallback(this.address, this::onMouseChanged);

		// Vanilla doesn't use this (currently), other mods might register this callback though ...
		this.isIconified = useBlazeSDLInitialState ? false : GLFW.glfwGetWindowAttrib(this.address, GLFW.GLFW_ICONIFIED) != 0;
		this.previousIconifyCallback = GLFW.glfwSetWindowIconifyCallback(this.address, this::onIconifyChanged);
	}

	private boolean isCurrentWindow(long address) {
		return address == this.address;
	}

	public long address() {
		return this.address;
	}

	public boolean isFocused() {
		return this.isFocused;
	}

	private void onFocusChanged(long address, boolean focused) {
		this.invokeFocus(address, focused);
	}

	/**
	 * Update the focus state and notify DynamicFPSMod. Used by both the
	 * chained GLFW callback and the BlazeSDL event bridge.
	 */
	public void invokeFocus(long address, boolean focused) {
		if (this.isCurrentWindow(address)) {
			this.isFocused = focused;
			DynamicFPSMod.onStatusChanged(true);
		}

		if (this.previousFocusCallback != null) {
			this.previousFocusCallback.invoke(address, focused);
		}
	}

	public boolean isHovered() {
		return this.isHovered;
	}

	private void onMouseChanged(long address, boolean hovered) {
		this.invokeCursorEnter(address, hovered);
	}

	/**
	 * Update the cursor-hover state and notify DynamicFPSMod. Used by both the
	 * chained GLFW callback and the BlazeSDL event bridge.
	 */
	public void invokeCursorEnter(long address, boolean hovered) {
		if (this.isCurrentWindow(address)) {
			this.isHovered = hovered;
			DynamicFPSMod.onStatusChanged(true);
		}

		if (this.previousMouseCallback != null) {
			this.previousMouseCallback.invoke(address, hovered);
		}
	}

	public boolean isIconified() {
		return this.isIconified;
	}

	private void onIconifyChanged(long address, boolean iconified) {
		this.invokeIconify(address, iconified);
	}

	/**
	 * Update the iconify state and notify DynamicFPSMod. Used by both the
	 * chained GLFW callback and the BlazeSDL event bridge.
	 */
	public void invokeIconify(long address, boolean iconified) {
		if (this.isCurrentWindow(address)) {
			this.isIconified = iconified;
			DynamicFPSMod.onStatusChanged(true);
		}

		if (this.previousIconifyCallback != null) {
			this.previousIconifyCallback.invoke(address, iconified);
		}
	}

	/**
	 * Seed the initial focus / iconify / hover state.
	 *
	 * <p>Under BlazeSDL we cannot use {@code glfwGetWindowAttrib} (it is not
	 * implemented for the SDL3 window), so the bridge computes the initial
	 * state from {@code SDL_GetWindowFlags} and pushes it here.
	 */
	public void setInitialState(boolean focused, boolean iconified, boolean hovered) {
		this.isFocused = focused;
		this.isIconified = iconified;
		this.isHovered = hovered;
	}
}
