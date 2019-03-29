A lifecycle aware coroutine dispatcher for Android.

Example usage:

```kotlin
GlobalScope.launch(lifecycleAwareDispatcher()) {
  // do some work on main thread.
}
```

The `lifecycleAwareDispatcher()` is an extension function to `LifecycleOwner`,
therefore it can be used in `Fragment` and `FragmentActivity`.

The primary purpose of this is to get lifecycle safety in Android. It ensures
that your coroutine running on Android main thread will only be resumed in
`STARTED` state of the lifecycle owner. It only supports dispatching on the main
thread.

The implementation is rather trivial. Just copy [the file](https://github.com/jinxinzheng/LifecycleAwareDispatcher/blob/master/dispatcher/src/main/java/com/zheng/android/coroutine/LifecycleAwareDispatcher.kt)
into your project and use it.
