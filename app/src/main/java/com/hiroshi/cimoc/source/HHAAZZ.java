package com.hiroshi.cimoc.source;

import android.util.Pair;

import com.hiroshi.cimoc.model.Chapter;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.ImageUrl;
import com.hiroshi.cimoc.model.Source;
import com.hiroshi.cimoc.parser.MangaCategory;
import com.hiroshi.cimoc.parser.MangaParser;
import com.hiroshi.cimoc.parser.NodeIterator;
import com.hiroshi.cimoc.parser.SearchIterator;
import com.hiroshi.cimoc.soup.Node;
import com.hiroshi.cimoc.utils.DecryptionUtils;
import com.hiroshi.cimoc.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by Hiroshi on 2016/7/26.
 */
public class HHAAZZ extends MangaParser {

    public static final int TYPE = 2;
    public static final String DEFAULT_TITLE = "手机汗汗";

    public boolean flag = true;
    private Headers headers;
    private static final String[] servers = {
            "http://www.hanhanmanhua.com",
    };

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    public HHAAZZ(Source source) {
        init(source, new Category());
    }

    @Override
    public Request getSearchRequest(String keyword, int page) {
        flag = true;
        if (page == 1) {
            String url = "http://www.hanhanmanhua.com/statics/search.aspx?key=".concat(keyword);
            return new Request.Builder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36")
                    .url(url)
                    .build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        return new NodeIterator(body.list("div.cy_list_mh ul")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.href("a.pic");
                String title = node.text("li.title");
                String cover = node.src("a.pic > img");
                String update = node.text("li.updata > a");
                String author = node.textWithSubstring("li.biaoqian", 3);
                return new Comic(TYPE, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        flag = true;
        String url = "http://www.hanhanmanhua.com/".concat(cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public void parseInfo(String html, Comic comic) {
        Node body = new Node(html);
        String title = body.text("div.cy_title > h1");
        String cover = body.src("div.cy_info_cover > a > img");
        String update = body.text("div.cy_zhangjie_top > p > a");
        String author = body.text("div.cy_xinxi a");
        String intro = body.text("#comic-description");
        boolean status = isFinish(body.text("div.cy_xinxi font"));
        comic.setInfo(title, cover, update, intro, author, status);
    }

    @Override
    public List<Chapter> parseChapter(String html) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        for (Node node : body.list("#mh-chapter-list-ol-0 li > a")) {
            String title = node.text();
            String path = node.hrefWithSplit(2);
            list.add(new Chapter(title, path));
        }
        return list;
    }

    @Override
    public List<Request> getImagesRequest(String cid, String path) {
        flag = true;
        String url = StringUtils.format("http://www.hanhanmanhua.com/%s/%s.html", cid, path);
        List<Request> requests = new ArrayList<>();
        requests.add(new Request.Builder().url(url).build());
        return requests;
    }

    @Override
    public List<ImageUrl> parseImages(String html) {
        List<ImageUrl> list = new LinkedList<>();
        try {
            String str = StringUtils.match("var qTcms_S_m_murl_e=(.*)", html, 1);

            String[] result = DecryptionUtils.base64Decrypt(str).split("\\$qingtiandy\\$");
            for (int i = 0; i != result.length; ++i) {
                if (result[i].startsWith("http")) {
                    flag = false;
                    headers = Headers.of("Referer", StringUtils.match("^https?://(.*?)/",result[i],1) + "/");
                    list.add(new ImageUrl(i + 1, result[i], false));
                }
                else
                    list.add(new ImageUrl(i + 1, buildUrl(result[i], servers), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private String[] unsuan(String str) {
        int num = str.length() - str.charAt(str.length() - 1) + 'a';
        String code = str.substring(num - 13, num - 3);
        String cut = str.substring(num - 3, num - 2);
        str = str.substring(0, num - 13);
        for (int i = 0; i < 10; ++i) {
            str = str.replace(code.charAt(i), (char) ('0' + i));
        }
        StringBuilder builder = new StringBuilder();
        String[] array = str.split(cut);
        for (int i = 0; i != array.length; ++i) {
            builder.append((char) Integer.parseInt(array[i]));
        }
        return builder.toString().split("\\|");
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        return new Node(html).textWithSubstring("div.main > div > div.pic > div.con > p:eq(5)", 5);
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new LinkedList<>();
        Node body = new Node(html);
        for (Node node : body.list("li.clearfix > a.pic")) {
            String cid = node.hrefWithSplit(1);
            String title = node.text("div.con > h3");
            String cover = node.src("img");
            String update = node.textWithSubstring("div.con > p > span", 0, 10);
            String author = node.text("div.con > p:eq(1)");
            list.add(new Comic(TYPE, cid, title, cover, update, author));
        }
        return list;
    }

    private static class Category extends MangaCategory {

        @Override
        public String getFormat(String... args) {
            if (!"".equals(args[CATEGORY_SUBJECT])) {
                return StringUtils.format("http://hhaass.com/lists/%s/%%d", args[CATEGORY_SUBJECT]);
            } else if (!"".equals(args[CATEGORY_AREA])) {
                return StringUtils.format("http://hhaass.com/lists/%s/%%d", args[CATEGORY_AREA]);
            } else if (!"".equals(args[CATEGORY_READER])) {
                return StringUtils.format("http://hhaass.com/duzhequn/%s/%%d", args[CATEGORY_PROGRESS]);
            } else if (!"".equals(args[CATEGORY_PROGRESS])) {
                return StringUtils.format("http://hhaass.com/lianwan/%s/%%d", args[CATEGORY_PROGRESS]);
            } else {
                return "http://hhaass.com/dfcomiclist_%d.htm";
            }
        }

        @Override
        protected List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("萌系", "1"));
            list.add(Pair.create("搞笑", "2"));
            list.add(Pair.create("格斗", "3"));
            list.add(Pair.create("科幻", "4"));
            list.add(Pair.create("剧情", "5"));
            list.add(Pair.create("侦探", "6"));
            list.add(Pair.create("竞技", "7"));
            list.add(Pair.create("魔法", "8"));
            list.add(Pair.create("神鬼", "9"));
            list.add(Pair.create("校园", "10"));
            list.add(Pair.create("惊栗", "11"));
            list.add(Pair.create("厨艺", "12"));
            list.add(Pair.create("伪娘", "13"));
            list.add(Pair.create("图片", "14"));
            list.add(Pair.create("冒险", "15"));
            list.add(Pair.create("耽美", "21"));
            list.add(Pair.create("经典", "22"));
            list.add(Pair.create("亲情", "25"));
            return list;
        }

        @Override
        protected boolean hasArea() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("大陆", "19"));
            list.add(Pair.create("香港", "20"));
            list.add(Pair.create("欧美", "23"));
            list.add(Pair.create("日文", "24"));
            return list;
        }

        @Override
        protected boolean hasReader() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getReader() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("少年", "1"));
            list.add(Pair.create("少女", "2"));
            list.add(Pair.create("青年", "3"));
            return list;
        }

        @Override
        protected boolean hasProgress() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("连载", "1"));
            list.add(Pair.create("完结", "2"));
            return list;
        }

    }

    @Override
    public Headers getHeader() {
        if(flag)
            return Headers.of("Referer", "http://hhaass.com/");
        else
            return headers;
    }

}
