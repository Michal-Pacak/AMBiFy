package pl.ambif.ambify.ui.branding

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val DYNAMIC_LOGO_ANIMATION_COUNT = 4
private const val LOGO_ASSET_DIRECTORY = "file:///android_asset/brand/"

// Point of the logo_dynamic.svg cycle where the whole logo is visible
// (rightGrow finishes at 58%, pillars start collapsing at 70%).
private const val FULL_LOGO_PROGRESS = 0.58

// Logo bounds in the 600x600 SVG (x 110..490, y 152..480) plus a small margin for the edge stroke.
// Aspect ratio 388:336 - size a cropped logo with the same ratio.
private const val LOGO_BOUNDS_VIEW_BOX = "106 148 388 336"

// Cropped bounds with room for an outline around the symbol. Aspect ratio 408:356.
private const val OUTLINED_LOGO_BOUNDS_VIEW_BOX = "96 138 408 356"

/**
 * Brand logo. The animated variant plays the build-up of the logo once,
 * stops on the fully visible logo and then calls [onAnimationFinished].
 * [cropToLogo] drops the empty margin of the 600x600 artboard.
 * With [outlineColor] the background is transparent and the symbol gets a 1 px outline
 * of that color, so content behind the logo is hidden only by the symbol itself.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    animated: Boolean,
    cropToLogo: Boolean = false,
    backgroundColor: Color = Color.Black,
    outlineColor: Color? = null,
    onAnimationFinished: () -> Unit = {},
    onAnimationError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val latestOnAnimationFinished = rememberUpdatedState(onAnimationFinished)
    val latestOnAnimationError = rememberUpdatedState(onAnimationError)
    val webView = remember(context, animated, cropToLogo, backgroundColor, outlineColor) {
        WebView(context).apply {
            setBackgroundColor(if (outlineColor != null) android.graphics.Color.TRANSPARENT else backgroundColor.toArgb())
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isClickable = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = true
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                blockNetworkLoads = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(false)
                javaScriptCanOpenWindowsAutomatically = false
            }

            if (animated) {
                addJavascriptInterface(
                    LogoAnimationBridge(
                        onFinished = { latestOnAnimationFinished.value() },
                        onError = { latestOnAnimationError.value(it) },
                    ),
                    "AMBiFyLogoAnimation",
                )
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(sizeSvgToViewportScript(cropToLogo, outlined = outlineColor != null), { result ->
                        if (result == "true") {
                            if (animated) {
                                view.evaluateJavascript(animationScript(), null)
                            }
                        } else if (animated) {
                            latestOnAnimationError.value(
                                "Nie udało się dopasować logo do rozmiaru ekranu.",
                            )
                        } else {
                            Log.e(
                                "AMBiFyBrandLogo",
                                "Unable to size static logo to the WebView viewport.",
                            )
                        }
                    })
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    if (animated && request.isForMainFrame) {
                        latestOnAnimationError.value(
                            "Nie udało się załadować animacji logo: ${error.description}",
                        )
                    }
                }
            }

            val assetPath = if (animated) {
                "brand/logo_dynamic.svg"
            } else {
                "brand/logo_static.svg"
            }
            val svg = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val document = """
                <!doctype html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                    <style>
                        html, body { width: 100%; height: 100%; min-height: 100vh; margin: 0; overflow: hidden; }
                        svg { position: absolute; inset: 0; display: block; width: 100vw; height: 100vh; }
                        ${outlineColor?.let { outlineStyle(it) }.orEmpty()}
                    </style>
                </head>
                <body>$svg</body>
                </html>
            """.trimIndent()
            loadDataWithBaseURL(
                LOGO_ASSET_DIRECTORY,
                document,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.removeJavascriptInterface("AMBiFyLogoAnimation")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = {},
    )
}

class LogoAnimationBridge(
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainThread = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onFinished() {
        mainThread.post(onFinished)
    }

    @JavascriptInterface
    fun onError(message: String) {
        mainThread.post { onError(message) }
    }
}

private fun animationScript(): String = """
    (function() {
        try {
            const elements = Array.from(document.querySelectorAll(
                '.anim-core, .anim-pillars-container, .anim-pillar-left, .anim-pillar-right'
            ));
            if (elements.length !== $DYNAMIC_LOGO_ANIMATION_COUNT) {
                throw new Error('Nie znaleziono wszystkich warstw animacji logo.');
            }
            const animations = elements.flatMap(function(element) {
                return element.getAnimations();
            });
            if (animations.length !== $DYNAMIC_LOGO_ANIMATION_COUNT) {
                throw new Error('Nie uruchomiono wszystkich animacji logo.');
            }

            const stopAt = animations[0].effect.getComputedTiming().duration * $FULL_LOGO_PROGRESS;
            function stopOnFullLogo() {
                const currentTime = animations[0].currentTime;
                if (currentTime === null || currentTime < stopAt) {
                    window.requestAnimationFrame(stopOnFullLogo);
                    return;
                }
                animations.forEach(function(animation) {
                    animation.pause();
                    animation.currentTime = stopAt;
                });
                window.AMBiFyLogoAnimation.onFinished();
            }
            window.requestAnimationFrame(stopOnFullLogo);
        } catch (error) {
            window.AMBiFyLogoAnimation.onError(String(error));
        }
    })();
""".trimIndent()

/** Transparent page and a 1 px outline following the shape of the symbol. */
private fun outlineStyle(color: Color): String {
    val rgb = "rgb(${(color.red * 255).toInt()}, ${(color.green * 255).toInt()}, ${(color.blue * 255).toInt()})"
    return "html, body { background: transparent; } " +
        "svg { filter: drop-shadow(1px 0 0 $rgb) drop-shadow(-1px 0 0 $rgb) " +
        "drop-shadow(0 1px 0 $rgb) drop-shadow(0 -1px 0 $rgb); }"
}

private fun sizeSvgToViewportScript(cropToLogo: Boolean, outlined: Boolean): String = """
    (function() {
        const svg = document.querySelector('svg');
        if (!svg || window.innerWidth <= 0 || window.innerHeight <= 0) {
            return false;
        }
        if ($cropToLogo) {
            svg.setAttribute('viewBox', '${if (outlined) OUTLINED_LOGO_BOUNDS_VIEW_BOX else LOGO_BOUNDS_VIEW_BOX}');
        }
        svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
        function fitToViewport() {
            const width = window.innerWidth + 'px';
            const height = window.innerHeight + 'px';
            svg.setAttribute('width', width);
            svg.setAttribute('height', height);
            svg.style.setProperty('width', width, 'important');
            svg.style.setProperty('height', height, 'important');
        }
        fitToViewport();
        // The view can be resized later (e.g. a larger logo), so keep the logo filling it.
        window.addEventListener('resize', fitToViewport);
        const bounds = svg.getBoundingClientRect();
        return bounds.width > 0 && bounds.height > 0;
    })();
""".trimIndent()
