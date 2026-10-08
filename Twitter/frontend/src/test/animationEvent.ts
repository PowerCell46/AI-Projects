// Without an `AnimationEvent`, React listens for the prefixed `webkitAnimationEnd` and never hears `animationend`, so
// tests could not end an animation by hand. This runs before React is imported, which is when React looks for it.
class AnimationEventDouble extends Event {}

Object.defineProperty(window, 'AnimationEvent', { value: AnimationEventDouble });
