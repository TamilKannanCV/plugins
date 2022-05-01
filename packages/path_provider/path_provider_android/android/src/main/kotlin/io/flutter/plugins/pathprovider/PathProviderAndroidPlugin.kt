// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package io.flutter.plugins.pathprovider

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.BinaryMessenger.TaskQueue
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.StandardMethodCodec
import io.flutter.util.PathUtils
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class PathProviderPlugin : FlutterPlugin, MethodCallHandler {
  private var context: Context? = null
  private var channel: MethodChannel? = null
  private var impl: PathProviderImpl? = null

  /**
   * An abstraction over how to access the paths in a thread-safe manner.
   *
   *
   * We need this so on versions of Flutter that support Background Platform Channels this plugin
   * can take advantage of it.
   *
   *
   * This can be removed after https://github.com/flutter/engine/pull/29147 becomes available on
   * the stable branch.
   */
  private interface PathProviderImpl {
    fun getTemporaryDirectory(@NonNull result: Result?)
    fun getApplicationDocumentsDirectory(@NonNull result: Result?)
    fun getStorageDirectory(@NonNull result: Result?)
    fun getExternalCacheDirectories(@NonNull result: Result?)
    fun getExternalStorageDirectories(@NonNull directoryName: String?, @NonNull result: Result?)
    fun getApplicationSupportDirectory(@NonNull result: Result?)
  }

  /** The implementation for getting system paths that executes from the platform  */
  private inner class PathProviderPlatformThread : PathProviderImpl {
    private val uiThreadExecutor: Executor = UiThreadExecutor()
    private val executor: Executor = Executors.newSingleThreadExecutor(
      ThreadFactoryBuilder()
        .setNameFormat("path-provider-background-%d")
        .setPriority(Thread.NORM_PRIORITY)
        .build()
    )

    override fun getTemporaryDirectory(@NonNull result: Result) {
      executeInBackground({ pathProviderTemporaryDirectory }, result)
    }

    override fun getApplicationDocumentsDirectory(@NonNull result: Result) {
      executeInBackground({ pathProviderApplicationDocumentsDirectory }, result)
    }

    override fun getStorageDirectory(@NonNull result: Result) {
      executeInBackground({ pathProviderStorageDirectory }, result)
    }

    override fun getExternalCacheDirectories(@NonNull result: Result) {
      executeInBackground<List<String>>(
        Callable<List<String?>> { pathProviderExternalCacheDirectories },
        result
      )
    }

    override fun getExternalStorageDirectories(
      @NonNull directoryName: String, @NonNull result: Result
    ) {
      executeInBackground<List<String>>(Callable<List<String?>> {
        getPathProviderExternalStorageDirectories(
          directoryName
        )
      }, result)
    }

    override fun getApplicationSupportDirectory(@NonNull result: Result) {
      executeInBackground({ applicationSupportDirectory }, result)
    }

    private fun <T> executeInBackground(task: Callable<T>, result: Result) {
      val future: SettableFuture<T> = SettableFuture.create()
      Futures.addCallback(
        future,
        object : FutureCallback<T>() {
          fun onSuccess(answer: T) {
            result.success(answer)
          }

          fun onFailure(t: Throwable) {
            result.error(t.javaClass.name, t.message, null)
          }
        },
        uiThreadExecutor
      )
      executor.execute {
        try {
          future.set(task.call())
        } catch (t: Throwable) {
          future.setException(t)
        }
      }
    }
  }

  /** The implementation for getting system paths that executes from a background thread.  */
  private inner class PathProviderBackgroundThread : PathProviderImpl {
    override fun getTemporaryDirectory(@NonNull result: Result) {
      result.success(pathProviderTemporaryDirectory)
    }

    override fun getApplicationDocumentsDirectory(@NonNull result: Result) {
      result.success(pathProviderApplicationDocumentsDirectory)
    }

    override fun getStorageDirectory(@NonNull result: Result) {
      result.success(pathProviderStorageDirectory)
    }

    override fun getExternalCacheDirectories(@NonNull result: Result) {
      result.success(pathProviderExternalCacheDirectories)
    }

    override fun getExternalStorageDirectories(
      @NonNull directoryName: String, @NonNull result: Result
    ) {
      result.success(getPathProviderExternalStorageDirectories(directoryName))
    }

    override fun getApplicationSupportDirectory(@NonNull result: Result) {
      result.success(applicationSupportDirectory)
    }
  }

  private fun setup(messenger: BinaryMessenger, context: Context) {
    val channelName = "plugins.flutter.io/path_provider_android"
    val taskQueue: TaskQueue = messenger.makeBackgroundTaskQueue()
    try {
      channel = MethodChannel(
        messenger,
        channelName,
        StandardMethodCodec.INSTANCE,
        taskQueue
      ) as MethodChannel?
      impl = PathProviderBackgroundThread()
    } catch (ex: Exception) {
      Log.e(TAG, "Received exception while setting up PathProviderPlugin", ex)
    }
    this.context = context
    channel.setMethodCallHandler(this)
  }

  fun onAttachedToEngine(@NonNull binding: FlutterPluginBinding) {
    setup(binding.getBinaryMessenger(), binding.getApplicationContext())
  }

  fun onDetachedFromEngine(@NonNull binding: FlutterPluginBinding?) {
    channel.setMethodCallHandler(null)
    channel = null
  }

  fun onMethodCall(call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getTemporaryDirectory" -> impl!!.getTemporaryDirectory(result)
      "getApplicationDocumentsDirectory" -> impl!!.getApplicationDocumentsDirectory(result)
      "getStorageDirectory" -> impl!!.getStorageDirectory(result)
      "getExternalCacheDirectories" -> impl!!.getExternalCacheDirectories(result)
      "getExternalStorageDirectories" -> {
        val type: Int = call.argument("type")
        val directoryName: String = StorageDirectoryMapper.androidType(type)
        impl!!.getExternalStorageDirectories(directoryName, result)
      }
      "getApplicationSupportDirectory" -> impl!!.getApplicationSupportDirectory(result)
      else -> result.notImplemented()
    }
  }

  private val pathProviderTemporaryDirectory: String
    private get() = context!!.cacheDir.path
  private val applicationSupportDirectory: String
    private get() = PathUtils.getFilesDir(context)
  private val pathProviderApplicationDocumentsDirectory: String
    private get() = PathUtils.getDataDirectory(context)
  private val pathProviderStorageDirectory: String?
    private get() {
      val dir = context!!.getExternalFilesDir(null) ?: return null
      return dir.absolutePath
    }
  private val pathProviderExternalCacheDirectories: List<String?>
    private get() {
      val paths: MutableList<String?> = ArrayList()
      if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
        for (dir in context!!.externalCacheDirs) {
          if (dir != null) {
            paths.add(dir.absolutePath)
          }
        }
      } else {
        val dir = context!!.externalCacheDir
        if (dir != null) {
          paths.add(dir.absolutePath)
        }
      }
      return paths
    }

  private fun getPathProviderExternalStorageDirectories(type: String): List<String?> {
    val paths: MutableList<String?> = ArrayList()
    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      for (dir in context!!.getExternalFilesDirs(type)) {
        if (dir != null) {
          paths.add(dir.absolutePath)
        }
      }
    } else {
      val dir = context!!.getExternalFilesDir(type)
      if (dir != null) {
        paths.add(dir.absolutePath)
      }
    }
    return paths
  }

  private class UiThreadExecutor : Executor {
    private val handler = Handler(Looper.getMainLooper())
    override fun execute(command: Runnable) {
      handler.post(command)
    }
  }

  companion object {
    const val TAG = "PathProviderPlugin"
    fun registerWith(registrar: io.flutter.plugin.common.PluginRegistry.Registrar) {
      val instance = PathProviderPlugin()
      instance.setup(registrar.messenger(), registrar.context())
    }
  }
}