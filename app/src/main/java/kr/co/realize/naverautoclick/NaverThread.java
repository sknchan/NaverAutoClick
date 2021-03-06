package kr.co.realize.naverautoclick;

import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class NaverThread extends Thread {
	public static final long MAX_WAIT_TIME = 15000;

    private MainActivity mainActivity;

    private NaverUtil naverUtil;
    private WebView webView;

	private List<NaverItem> itemList;
	private long delay;
	private NaverThreadListener listener;

	private int index;

	public NaverThread(MainActivity mainActivity, WebView webView, List<NaverItem> itemList, long delay, NaverThreadListener listener) {
        this.mainActivity = mainActivity;

        this.naverUtil = new NaverUtil(webView, listener);
        this.webView = webView;

		this.itemList = itemList;
		this.delay = delay;
		this.listener = listener;
	}

	@Override
	public void run() {
		ListIterator<NaverItem> iterator = null;
		
		while (true) {
			if (iterator != null && iterator.hasNext()) {
				final NaverItem item = iterator.next();
				
				if (item == null || item.query.equals("") || item.url.equals("")) {
					continue;
				}

                naverUtil.clearPageFinishedQueue();
				
				index = itemList.indexOf(item) + 1;
				
				Log.i("naverautoclick", index + "번째 링크 시작되었습니다.");
				
				try {
					listener.onLog(index + "번째 링크 시작되었습니다.");
					
					listener.onLog(index + "번째 링크 검색 시작되었습니다.");
					this.search(item);
					listener.onLog(index + "번째 링크 검색 완료되었습니다.");
					
					listener.onLog(index + "번째 링크 페이지 탐색 시작되었습니다.");
					boolean pageFounded = false;
                    if(item.kind == NaverItem.KIND_IMAGE) pageFounded = find_Image(item);
                    else if(item.kind == NaverItem.KIND_MAP) pageFounded = find_Map(item);
                    else pageFounded = find(item);

					listener.onLog(index + "번째 링크 페이지 탐색 완료되었습니다.");
					
					if (pageFounded) {
						listener.onLog(index + "번째 링크 체류를 시작합니다.");
						Thread.sleep(delay + (long) (Math.random() * 10000));
						item.countClicked++;
						listener.onLog(index + "번째 링크 체류가 끝났습니다.");

                        if(item.kind == NaverItem.KIND_BLOG){
                            int count = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(".lst_t2 ul li a")+".length"));
                            listener.onLog("카운트: "+count+", 랜덤: "+(int)(Math.random()*count));
                            if(count!=0){
                                listener.onLog("블로그 영역이동을 진행합니다.");
                                naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(".lst_t2 ul li a", (int)(Math.random()*count)));
                                Thread.sleep(7000+(long) (Math.random() * 5000));
                                listener.onLog("블로그 영역이동을 완료되었습니다.");
                            }
                        }

                        else if(item.kind != NaverItem.KIND_MAP && item.kind != NaverItem.KIND_SITE) {
						    for (int i=0; i<2; i++) {
                                naverUtil.synchronizedLoadUrl("javascript:history.back()");
                                naverUtil.loadUrl("javascript:" + naverUtil.click(".sch_tab button"));
                                int numTab = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(".sch_tab li") + ".length"));
                                if (naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(".sch_tab li a", (int) (Math.random() * numTab))).contains("&url=")) {
                                    naverUtil.waitLoadUrl();
                                }
                                naverUtil.waitRandomSeconds();
                            }
						}
					} else {
						listener.onLog(index + "번째 링크를 찾지 못했습니다.");
					}

					listener.onLog(index + "번째 링크 초기화 중 입니다...");
					/*webView.post(new Runnable() {
						
						@Override
						public void run() {
							webView.clearCache(true);
						}
					});*/
					CookieManager.getInstance().removeAllCookie();
					listener.onLog(index + "번째 링크 IP를 변경하는 중 입니다...");
                    naverUtil.changeNetworkState();
					
					listener.onLog(index + "번째 링크 끝났습니다.");
					listener.onComplete(item);
				} catch (InterruptedException e) {
					break;
				} catch (NullPointerException e) {
					Log.e("naverautoclick", index + "번째 링크 응답이 없어 재시작합니다.");
					listener.onLog(index + "번째 링크 응답이 없어 재시작합니다.");
					iterator.previous();
					
					final BlockingQueue<String> waitChangeNetworkStateQueue = new ArrayBlockingQueue<String>(1);
					webView.post(new Runnable() {
						
						@Override
						public void run() {
							webView.stopLoading();
							try {
                                naverUtil.changeNetworkState();
							} catch (Exception e) {
								e.printStackTrace();
							}
							waitChangeNetworkStateQueue.add("");
						}
					});
					try {
						waitChangeNetworkStateQueue.take();
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
					listener.onException(e);
					iterator.previous();
				}
			} else {
				iterator = itemList.listIterator();
                final BlockingQueue<Boolean> queue = new ArrayBlockingQueue<Boolean>(1);
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.validate(queue);
                    }
                });
                try {
                    if (!queue.take()) {
                        return;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
		}
	}

    private boolean find_Map(NaverItem item) throws Exception{
        boolean pageFounded = false;

        naverUtil.waitRandomSeconds();
        for (int page = 1; page<=15; page++){
            listener.onLog(index + "번째 링크 " + page + "번째 페이지 탐색 중 입니다..");


            int countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll("._item > a")+".length"));
            for(int i=0; i<countLinkes; i++){
                final String href = naverUtil.requestResult(naverUtil.querySelectorAll("._item > a")+"["+i+"].getAttribute('data-cid')");

                if(i==0) Log.i("item", item.url);
                Log.i("href", href);
                if(item.url.contains(href)){
                    //item.rank = i+1;

                    item.rank = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll("._linkSiteview")+"["+i+"].getAttribute('data-rank')"));
                    listener.onRankChanged(item);
                    naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.querySelectorAll("._linkSiteview")+"["+i+"].click()");
                    //naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click("._linkSiteview", i)); 두번해도
                    //naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click("._linkSiteview", i)); 안되잖아

                    pageFounded = true;
                    break;
                }
            }

            if (pageFounded || page == 14) {
                break;
            }


            naverUtil.requestResult("document.body.scrollTop = document.body.scrollHeight");
            //this.synchronizedLoadUrl("javascript:"+click(".u_pg_btn[href='#']"));
            naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click("#moreViewBtn a"), 1);
            naverUtil.waitRandomSeconds();
        }


        return pageFounded;
    }

    private boolean find(NaverItem item) throws Exception{
        boolean pageFounded = false;
        int rank = 0;

        for (int page=1; page<=14; page++) {
            listener.onLog(index + "번째 링크 " + page + "번째 페이지 탐색 중 입니다..");
            naverUtil.waitRandomSeconds();
            String type = ".uni li > a";
            int countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(type)+".length"));
            if(countLinkes==0){
                if(item.kind == NaverItem.KIND_FUSION || item.kind == NaverItem.KIND_BLOG) {
                    type = ".sp_total li > a";
                    countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(type) + ".length"));
                }
                else if(item.kind == NaverItem.KIND_IMAGE){
                    type = ".item_photo > a";
                    countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(type) + ".length"));
                }
                else if(item.kind == NaverItem.KIND_SITE){
                    type = ".sp_site li div div > a";
                    countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(type) + ".length"));
                }
                else break;
            }

            for (int i=0; i<countLinkes; i++) {
                String href = naverUtil.requestResult(naverUtil.querySelectorAll(type) + "[" + i + "].getAttribute('href')");




                if (href.contains(item.url)) {
                    //item.rank = Integer.parseInt(id.substring(7));

                    if(item.kind == NaverItem.KIND_FUSION){
                        String id = naverUtil.requestResult(naverUtil.querySelectorAll(type) + "[" + i + "].parentNode.getAttribute('id')");
                        item.rank = Integer.parseInt(id.substring(7));
                    }
                    else if(item.kind == NaverItem.KIND_BLOG){
                        String id = naverUtil.requestResult(naverUtil.querySelectorAll(type) + "[" + i + "].parentNode.getAttribute('id')");
                        item.rank = Integer.parseInt(id.substring(5));
                    }
                    else if(item.kind == NaverItem.KIND_SITE){
                        item.rank = (page-1)*15+1+i;
                        //listener.onLog("page== "+page+", i== "+i);
                    }
                    else break;



                    listener.onRankChanged(item);


                    naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(type+"[href='" + href+"']"));
                    //listener.onLog("javascript:" + click(type+"[href='" + href +"']"));
                    //listener.onLog(type+"[href=']"+href+"]'");

                    naverUtil.waitLoadUrl();
                    naverUtil.softSynchronizedLoadUrl("javascript:void()");

                    pageFounded = true;
                    break;
                }

            }



            if (pageFounded || page == 14) {
                break;
            }




            if (naverUtil.requestResult(naverUtil.querySelectorAll(".pg2b_btn") + "[1].getAttribute('class')").contains("dim")) {
                break;
            }

            naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(".pg2b_btn", 1));
        }


        return pageFounded;
    }

    private boolean find_Image(NaverItem item) throws Exception{
        boolean pageFounded = false;
        int rank = 0;

        naverUtil.requestResult("document.body.scrollTop = document.body.scrollHeight");
        naverUtil.waitRandomSeconds();
        for (int page = 1; page<=100; page++){
            //int countLinkes = Integer.parseInt(javascriptInterface.requestResult(webView, querySelectorAll(".item_photo a")));


            listener.onLog(index + "번째 링크 " + page + "번째 페이지 탐색 중 입니다..");


            int countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(".item_photo > a")+".length"));
            for(int i=0; i<countLinkes; i++){
                if(i<rank) continue;

                final String href = naverUtil.requestResult(naverUtil.querySelectorAll(".item_photo > a")+"["+i+"].getAttribute('href')");
                if(href.contains(item.url)){
                    item.rank = i+1;
                    listener.onRankChanged(item);
                    //naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click(".item_photo a [href='"+href+"']"));
                    naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click(".item_photo a", i));
                    pageFounded = true;
                    break;
                }
            }

            if (pageFounded || page == 99) {
                break;
            }
            rank=countLinkes;

            naverUtil.requestResult("document.body.scrollTop = document.body.scrollHeight");
            naverUtil.synchronizedLoadUrl("javascript:"+naverUtil.click(".u_pg_btn[href='#']"));
            naverUtil.waitRandomSeconds();
        }


        return pageFounded;
    }
	
	/*private boolean find(NaverItem item) throws Exception {
		boolean pageFounded = false;
		
		for (int page=1; page<=14; page++) {
			listener.onLog(index + "번째 링크 " + page + "번째 페이지 탐색 중 입니다..");
            naverUtil.waitRandomSeconds();
			
			String type = "uni";
			int countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(".uni li > a") + ".length"));
			if (countLinkes == 0) {
				type = "sp_total";
				countLinkes = Integer.parseInt(naverUtil.requestResult(naverUtil.querySelectorAll(".sp_total li > a") + ".length"));
			}
			Log.d("naverautoclick", Integer.toString(countLinkes));
			for (int i=0; i<countLinkes; i++) {
				final String href = naverUtil.requestResult(naverUtil.querySelectorAll("." + type + " li > a") + "[" + i + "].getAttribute('href')");
				final String hasId = naverUtil.requestResult(naverUtil.querySelectorAll("." + type + " li > a") + "[" + i + "].parentNode.hasAttribute('id')");
				if (hasId.equals("false")) {
					continue;
				}
				final String id = naverUtil.requestResult(naverUtil.querySelectorAll("." + type + " li > a") + "[" + i + "].parentNode.getAttribute('id')");
				
				if (href.contains(item.url)) {
					item.rank = Integer.parseInt(id.substring(7));
					listener.onRankChanged(item);

                    naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(".sc a[href='" + href + "']"));
                    naverUtil.waitLoadUrl();
                    naverUtil.softSynchronizedLoadUrl("javascript:void()");
					
					pageFounded = true;
					break;
				}
			}
			
			if (pageFounded || page == 14) {
				break;
			}
			
			if (naverUtil.requestResult(naverUtil.querySelectorAll(".pg2b_btn") + "[1].getAttribute('class')").contains("dim")) {
				break;
			}

            naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click(".pg2b_btn", 1));
		}
		
		return pageFounded;
	}*/

	private void search(NaverItem item) throws Exception {

        naverUtil.synchronizedLoadUrl("http://m.naver.com");

        naverUtil.softSynchronizedLoadUrl("javascript:" + naverUtil.click("#query"));
        naverUtil.loadUrl("javascript:void(" + naverUtil.querySelector("#query") + ".value = '" + item.query + "');");

        naverUtil.waitRandomSeconds();

        naverUtil.synchronizedLoadUrl("javascript:" + naverUtil.click("button[type=submit]"));

        if(item.kind != NaverItem.KIND_FUSION)
            naverUtil.loadUrl("javascript:" + naverUtil.click(".sch_tab button"));

        if(item.kind == NaverItem.KIND_IMAGE)
            naverUtil.synchronizedLoadUrl("javascript: " + naverUtil.click(".sch_tab li a[href*='m_image']"));

        else if(item.kind == NaverItem.KIND_BLOG)
            naverUtil.synchronizedLoadUrl("javascript: " + naverUtil.click(".sch_tab li a[href*='m_blog']"));

        else if(item.kind == NaverItem.KIND_MAP)
            naverUtil.synchronizedLoadUrl("javascript: " + naverUtil.click(".sch_tab li a[href*='map.naver.com']"));

        else if(item.kind == NaverItem.KIND_SITE)
            naverUtil.synchronizedLoadUrl("javascript: " + naverUtil.click(".sch_tab li a[href*='m_site']"));
	}


}
