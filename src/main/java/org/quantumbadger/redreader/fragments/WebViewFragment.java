/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.holoeverywhere.LayoutInflater;
import org.holoeverywhere.app.Fragment;
import org.holoeverywhere.widget.FrameLayout;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.account.RedditAccountManager;
import org.quantumbadger.redreader.cache.CacheManager;
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost;
import org.quantumbadger.redreader.reddit.things.RawRedditPost;
import org.quantumbadger.redreader.reddit.things.RawRedditSubreddit;
import org.quantumbadger.redreader.views.RedditPostView;
import org.quantumbadger.redreader.views.WebViewFixed;
import org.quantumbadger.redreader.views.bezelmenu.BezelSwipeOverlay;
import org.quantumbadger.redreader.views.bezelmenu.SideToolbarOverlay;
import org.quantumbadger.redreader.views.liststatus.LoadingView;

public class WebViewFragment extends Fragment implements RedditPostView.PostSelectionListener {

	private String url;
    private String currentUrl;
    private boolean goingBack;

	private WebViewFixed webView;
	private LoadingView loadingView;
	private FrameLayout outer;

	public static WebViewFragment newInstance(final String url, final RawRedditPost post) {

		final WebViewFragment f = new WebViewFragment();

		final Bundle bundle = new Bundle(1);
		bundle.putString("url", url);
		if(post != null) bundle.putParcelable("post", post);
		f.setArguments(bundle);

		return f;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		// TODO load position/etc?
		super.onCreate(savedInstanceState);
		url = getArguments().getString("url");
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

		final Context context = inflater.getContext();

		outer = (FrameLayout)inflater.inflate(R.layout.web_view_fragment);

		final RawRedditPost src_post = getArguments().getParcelable("post");
		final RedditPreparedPost post = src_post == null ? null
				: new RedditPreparedPost(context, CacheManager.getInstance(context), 0, src_post, -1, false,
				new RawRedditSubreddit("/r/" + src_post.subreddit, src_post.subreddit, false),
				false, false, false, RedditAccountManager.getInstance(context).getDefaultAccount());

		webView = (WebViewFixed)outer.findViewById(R.id.web_view_fragment_webviewfixed);
		final FrameLayout loadingViewFrame = (FrameLayout)outer.findViewById(R.id.web_view_fragment_loadingview_frame);

		loadingView = new LoadingView(context);
		loadingViewFrame.addView(loadingView);

		final WebSettings settings = webView.getSettings();

		settings.setBuiltInZoomControls(true);
		settings.setJavaScriptEnabled(true);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);

		try {
			settings.setDisplayZoomControls(false);
		} catch(NoSuchMethodError e) {
			// Old version of Android...
		}

		// TODO handle long clicks

		webView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                // Go back if loading same page to prevent redirect loops.
                if(goingBack && currentUrl != null && url != null && url.equals(currentUrl)) {
                    if (webView.canGoBackOrForward(-2)) {
                        webView.goBackOrForward(-2);
                    } else {
                        getSupportActivity().finish();
                    }
                } else  {
                    // TODO handle reddit URLs in the app
                    webView.loadUrl(url);
                    currentUrl = url;
                }
                return true;
			}

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
				getSupportActivity().setTitle(url);
			}

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                goingBack = false;
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
            }
        });

		webView.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {

				super.onProgressChanged(view, newProgress);

				loadingView.setProgress(R.string.download_downloading, (float)newProgress / 100.0f);
				loadingView.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
			}
		});


		webView.loadUrl(url);

		final FrameLayout outerFrame = new FrameLayout(context);
		outerFrame.addView(outer);

		if(post != null) {

			final SideToolbarOverlay toolbarOverlay = new SideToolbarOverlay(context);

			final BezelSwipeOverlay bezelOverlay = new BezelSwipeOverlay(context, new BezelSwipeOverlay.BezelSwipeListener() {

				public boolean onSwipe(BezelSwipeOverlay.SwipeEdge edge) {

					toolbarOverlay.setContents(post.generateToolbar(context, WebViewFragment.this, toolbarOverlay));
					toolbarOverlay.show(edge == BezelSwipeOverlay.SwipeEdge.LEFT ?
							SideToolbarOverlay.SideToolbarPosition.LEFT : SideToolbarOverlay.SideToolbarPosition.RIGHT);
					return true;
				}

				public boolean onTap() {

					if(toolbarOverlay.isShown()) {
						toolbarOverlay.hide();
						return true;
					}

					return false;
				}
			});

			outerFrame.addView(bezelOverlay);
			outerFrame.addView(toolbarOverlay);

			bezelOverlay.getLayoutParams().width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
			bezelOverlay.getLayoutParams().height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;

			toolbarOverlay.getLayoutParams().width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
			toolbarOverlay.getLayoutParams().height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
		}

		return outerFrame;
	}

	@Override
	public void onDestroyView() {

		webView.stopLoading();
		webView.loadData("<html></html>", "text/plain", "UTF-8");
		webView.reload();
		webView.loadUrl("about:blank");
		outer.removeAllViews();
		webView.destroy();

		super.onDestroyView();
	}

	public boolean onBackButtonPressed() {

		if(webView.canGoBack()) {
            goingBack = true;
			webView.goBack();
			return true;
		}

		return false;
	}

	public void onPostSelected(final RedditPreparedPost post) {
		((RedditPostView.PostSelectionListener)getSupportActivity()).onPostSelected(post);
	}

	public void onPostCommentsSelected(final RedditPreparedPost post) {
		((RedditPostView.PostSelectionListener)getSupportActivity()).onPostCommentsSelected(post);
	}

    public String getCurrentUrl() {
        return (currentUrl != null) ? currentUrl : url;
    }

	@Override
	public void onPause() {
		super.onPause();
		webView.onPause();
		webView.pauseTimers();
	}

	@Override
	public void onResume() {
		super.onResume();
		webView.resumeTimers();
		webView.onResume();
	}
}
