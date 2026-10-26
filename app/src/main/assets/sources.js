// Velvet Index — source adapters.
//
// kind:'json'  url() -> JSON endpoint, parse(json) -> rows
// kind:'html'  url() -> search page,  parseHtml(html) -> rows
//              `chunk` is a literal the Java side splits on, so only the result
//              blocks cross the JS bridge (tube pages are ~700 KB otherwise)
// kind:'video' served by an optional Lustpress-compatible API
//
// Optional per source:
//   embedUrl(pageUrl)  a much lighter URL whose HTML still contains the stream
//   resolve(html)      extract the direct mp4/m3u8
//   fallbackUrl(q, p)  retried when the primary returns nothing
//
// Nothing is filtered. If a site returns it, you see it.

const UA = 'VelvetIndex/1.1 (Android; +https://github.com/JordanSosa/VelvetIndexAndroid)';

const SCRAPE_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.9'
};

const enc = encodeURIComponent;

function pref(k) { try { return Native.getPref(k) || ''; } catch (e) { return ''; } }

function creds() {
  const k = pref('gelbooru_key'), u = pref('gelbooru_user');
  return (k && u) ? '&api_key=' + enc(k) + '&user_id=' + enc(u) : '';
}

/* ---------------------------------------------------------------- helpers */

function rx(s, re, g) { const m = String(s == null ? '' : s).match(re); return m ? (m[g || 1] || '') : ''; }

function unesc(s) {
  return String(s == null ? '' : s)
    .replace(/\\\//g, '/')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&#x27;/gi, "'")
    .replace(/&nbsp;/g, ' ').trim();
}

function chunks(html, marker) {
  const parts = String(html == null ? '' : html).split(marker);
  parts.shift();
  return parts;
}

/** pick the best-quality mp4 out of a blob of page HTML */
function bestMp4(html) {
  const s = String(html == null ? '' : html).replace(/\\\//g, '/');
  const urls = s.match(/https?:\/\/[^\s"'<>\\)]+\.mp4[^\s"'<>\\)]*/g) || [];
  if (!urls.length) return '';
  const score = u => {
    let n = 0;
    if (/1080/.test(u)) n += 200; else if (/720/.test(u)) n += 120;
    else if (/480/.test(u)) n += 60; else if (/360/.test(u)) n += 30;
    if (/preview|trailer|_dmb_|thumb|_1\.mp4/i.test(u)) n -= 45;
    if (/mobile|_m\.mp4|_low/i.test(u)) n -= 12;
    return n;
  };
  const seen = {};
  return urls.filter(u => { if (seen[u]) return false; seen[u] = 1; return true; })
              .sort((a, b) => score(b) - score(a))[0] || '';
}

function bestHls(html) {
  const s = String(html == null ? '' : html).replace(/\\\//g, '/');
  const m = s.match(/https?:\/\/[^\s"'<>\\)]+\.m3u8[^\s"'<>\\)]*/g);
  return m && m.length ? m[0] : '';
}

const anyStream = html => bestMp4(html) || bestHls(html);

/* ---------------------------------------------------------------- sources */

const SOURCES = [
  /* ============================ BOORUS ============================ */
  {
    id: 'e621', label: 'e621', group: 'Booru', enabled: true, kind: 'json',
    note: 'Furry + western. Deepest tag coverage anywhere. Rate limited to ~2/s.',
    headers: { 'User-Agent': UA, 'Accept': 'application/json' },
    url: (q, page) => 'https://e621.net/posts.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q ? q + ' order:id_desc' : 'order:id_desc'),
    parse: j => (j.posts || []).map(p => ({
      id: p.id,
      page_url: 'https://e621.net/posts/' + p.id,
      file_url: p.file && p.file.url,
      preview_url: (p.preview && p.preview.url) || (p.sample && p.sample.url),
      title: 'e621 #' + p.id,
      rating: p.rating,
      score: (p.score && p.score.total) || 0,
      media_type: /webm|mp4|swf/i.test((p.file && p.file.ext) || '') ? 'video' : 'image',
      tags: ['general', 'species', 'character', 'copyright', 'artist', 'meta']
              .flatMap(k => (p.tags && p.tags[k]) || []).join(' ')
    }))
  },
  {
    id: 'rule34', label: 'Rule34', group: 'Booru', enabled: true, kind: 'json',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid='
                    + page + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://rule34.xxx/index.php?page=post&s=view&id=' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url || p.sample_url,
      title: 'Rule34 #' + p.id,
      score: parseInt(p.score, 10) || 0,
      media_type: /\.(mp4|webm)(\?|$)/i.test(p.file_url || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'gelbooru', label: 'Gelbooru', group: 'Booru', enabled: true, kind: 'json',
    note: 'Add api_key + user_id in Settings if you get 401s.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid='
                    + page + '&tags=' + enc(q || 'all') + creds(),
    parse: j => ((j && j.post) || []).map(p => ({
      id: p.id,
      page_url: 'https://gelbooru.com/index.php?page=post&s=view&id=' + p.id,
      file_url: p.file_url,
      preview_url: p.preview_url,
      title: 'Gelbooru #' + p.id,
      rating: p.rating,
      score: parseInt(p.score, 10) || 0,
      media_type: /\.(mp4|webm)(\?|$)/i.test(p.file_url || '') ? 'video' : 'image',
      tags: p.tags
    }))
  },
  {
    id: 'danbooru', label: 'Danbooru', group: 'Booru', enabled: false, kind: 'json',
    note: 'Best anime tag vocabulary. Anonymous access is capped at 2 tags.',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://danbooru.donmai.us/posts.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id,
      page_url: 'https://danbooru.donmai.us/posts/' + p.id,
      file_url: p.file_url || p.large_file_url,
      preview_url: p.preview_file_url,
      title: 'Danbooru #' + p.id,
      rating: p.rating,
      score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image',
      tags: p.tag_string
    }))
  },
  {
    id: 'konachan', label: 'Konachan', group: 'Booru', enabled: false, kind: 'json',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://konachan.com/post.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id, page_url: 'https://konachan.com/post/show/' + p.id,
      file_url: p.file_url, preview_url: p.preview_url, title: 'Konachan #' + p.id,
      rating: p.rating, score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image', tags: p.tags
    }))
  },
  {
    id: 'yandere', label: 'yande.re', group: 'Booru', enabled: false, kind: 'json',
    headers: { 'User-Agent': UA },
    url: (q, page) => 'https://yande.re/post.json?limit=60&page=' + (page + 1)
                    + '&tags=' + enc(q || 'all'),
    parse: j => (Array.isArray(j) ? j : []).map(p => ({
      id: p.id, page_url: 'https://yande.re/post/show/' + p.id,
      file_url: p.file_url, preview_url: p.preview_url, title: 'yande.re #' + p.id,
      rating: p.rating, score: p.score || 0,
      media_type: /webm|mp4/i.test(p.file_ext || '') ? 'video' : 'image', tags: p.tags
    }))
  },

  /* ============================= TUBES ============================= */
  {
    id: 'eporner', label: 'EPorner', group: 'Tubes', enabled: true, kind: 'json',
    note: 'Keyless public JSON API. The most reliable tube source. Good JAV coverage.',
    headers: SCRAPE_HEADERS,
    url: (q, page) => 'https://www.eporner.com/api/v2/video/search/?query=' + enc(q)
                    + '&per_page=40&page=' + (page + 1) + '&thumbsize=big&format=json',
    parse: j => (j.videos || []).map(v => ({
      id: v.id,
      page_url: unesc(v.url),
      preview_url: unesc((v.default_thumb && v.default_thumb.src) || ''),
      title: unesc(v.title),
      score: Number(v.views) || 0,
      notes: 'duration ' + (v.length_min || '?') + (v.rate ? ' - rated ' + v.rate : ''),
      media_type: 'video',
      tags: unesc(v.keywords || '') + ' eporner'
    })),
    embedUrl: p => { const id = rx(p, /video-([A-Za-z0-9]+)/); return id ? 'https://www.eporner.com/embed/' + id + '/' : ''; },
    resolve: anyStream
  },
  {
    id: 'redtube', label: 'RedTube', group: 'Tubes', enabled: true, kind: 'json',
    note: 'Keyless public JSON API.',
    headers: SCRAPE_HEADERS,
    url: (q, page) => 'https://api.redtube.com/?data=redtube.Videos.searchVideos&search=' + enc(q)
                    + '&page=' + (page + 1) + '&output=json&thumbsize=big',
    parse: j => ((j && j.videos) || []).map(x => {
      const v = x.video || x;
      return {
        id: v.video_id,
        page_url: unesc(v.url),
        preview_url: unesc(v.thumb || v.default_thumb || ''),
        title: unesc(v.title),
        score: Number(v.views) || 0,
        media_type: 'video',
        tags: unesc(((v.tags || []).map(t => t.tag_name).join(' ')) + ' redtube')
      };
    }),
    embedUrl: p => { const id = rx(p, /\/(\d+)/); return id ? 'https://www.redtube.com/embed/' + id : ''; },
    resolve: anyStream
  },
  {
    id: 'xvideos', label: 'XVideos', group: 'Tubes', enabled: true, kind: 'html',
    note: 'Scraped search + stream extraction from the embed frame.',
    headers: SCRAPE_HEADERS,
    chunk: '<div class="thumb-block',
    url: (q, page) => 'https://www.xvideos.com/?k=' + enc(q) + '&p=' + (page + 1),
    parseHtml: html => chunks(html, '<div class="thumb-block').map(c => {
      const href = rx(c, /href="(\/video[^"]+)"/);
      if (!href) return null;
      return {
        page_url: 'https://www.xvideos.com' + href,
        preview_url: unesc(rx(c, /data-src="(https?:\/\/[^"]+)"/) || rx(c, /<img[^>]+src="(https?:\/\/[^"]+)"/)),
        title: unesc(rx(c, /title="([^"]+)"/)) || 'XVideos',
        notes: rx(c, /class="duration">\s*([^<]+?)\s*</) ? 'duration ' + rx(c, /class="duration">\s*([^<]+?)\s*</) : '',
        media_type: 'video',
        tags: 'xvideos'
      };
    }).filter(Boolean),
    embedUrl: p => { const id = rx(p, /\/video\.([^\/?]+)/); return id ? 'https://www.xvideos.com/embedframe/' + id : ''; },
    resolve: anyStream
  },
  {
    id: 'xnxx', label: 'XNXX', group: 'Tubes', enabled: true, kind: 'html',
    headers: SCRAPE_HEADERS,
    chunk: '<div class="thumb-block',
    url: (q, page) => 'https://www.xnxx.com/?k=' + enc(q) + '&p=' + (page + 1),
    parseHtml: html => chunks(html, '<div class="thumb-block').map(c => {
      const href = rx(c, /href="(\/video[^"]+)"/);
      if (!href) return null;
      return {
        page_url: 'https://www.xnxx.com' + href,
        preview_url: unesc(rx(c, /data-src="(https?:\/\/[^"]+)"/) || rx(c, /<img[^>]+src="(https?:\/\/[^"]+)"/)),
        title: unesc(rx(c, /title="([^"]+)"/)) || 'XNXX',
        media_type: 'video',
        tags: 'xnxx'
      };
    }).filter(Boolean),
    embedUrl: p => { const id = rx(p, /\/video[-\.\/]([^\/?]+)/); return id ? 'https://www.xnxx.com/embedframe/' + id : ''; },
    resolve: anyStream
  },
  {
    id: 'pornhub', label: 'Pornhub', group: 'Tubes', enabled: true, kind: 'html',
    note: 'A/B tested markup, so this one is the most likely to need a tweak.',
    headers: SCRAPE_HEADERS,
    chunk: 'data-video-id="',
    url: (q, page) => 'https://www.pornhub.com/video/search?search=' + enc(q) + '&page=' + (page + 1),
    parseHtml: html => chunks(html, 'data-video-id="').map(c => {
      const key = rx(c, /\/view_video\.php\?viewkey=([A-Za-z0-9]+)/);
      if (!key) return null;
      return {
        page_url: 'https://www.pornhub.com/view_video.php?viewkey=' + key,
        preview_url: unesc(rx(c, /data-src="(https?:\/\/[^"]+)"/) || rx(c, /data-mediumthumb="(https?:\/\/[^"]+)"/) || rx(c, /<img[^>]+src="(https?:\/\/[^"]+)"/)),
        title: unesc(rx(c, /title="([^"]+)"/)) || 'Pornhub',
        notes: rx(c, /class="duration">([^<]+)</) ? 'duration ' + rx(c, /class="duration">([^<]+)</) : '',
        media_type: 'video',
        tags: 'pornhub'
      };
    }).filter(Boolean),
    embedUrl: p => { const k = rx(p, /viewkey=([A-Za-z0-9]+)/); return k ? 'https://www.pornhub.com/embed/' + k : ''; },
    resolve: anyStream
  },
  {
    id: 'xhamster', label: 'xHamster', group: 'Tubes', enabled: true, kind: 'html',
    headers: SCRAPE_HEADERS,
    chunk: 'thumb-list__item',
    url: (q, page) => 'https://xhamster.com/search/' + enc(q) + '?page=' + (page + 1),
    parseHtml: html => chunks(html, 'thumb-list__item').map(c => {
      const href = rx(c, /href="(https:\/\/xhamster\.com\/videos\/[^"]+)"/);
      if (!href) return null;
      return {
        page_url: unesc(href),
        preview_url: unesc(rx(c, /data-src="(https?:\/\/[^"]+)"/) || rx(c, /src="(https?:\/\/[^"]+\.jpe?g[^"]*)"/)),
        title: unesc(rx(c, /title="([^"]+)"/) || rx(c, /<a[^>]+>([^<]{4,})<\/a>/)) || 'xHamster',
        media_type: 'video',
        tags: 'xhamster'
      };
    }).filter(Boolean),
    embedUrl: p => { const id = rx(p, /-(\d+)(\?|$)/); return id ? 'https://xhamster.com/xembed.php?video=' + id : ''; },
    resolve: anyStream
  },
  {
    id: 'spankbang', label: 'SpankBang', group: 'Tubes', enabled: true, kind: 'html',
    note: 'Flakiest of the set. Relies on the page stream_url.',
    headers: SCRAPE_HEADERS,
    chunk: 'class="video-item',
    url: (q, page) => 'https://spankbang.com/s/' + enc(q) + '/' + (page + 1) + '/',
    parseHtml: html => chunks(html, 'class="video-item').map(c => {
      const href = rx(c, /href="(\/[a-z0-9]+\/video\/[^"]+)"/);
      if (!href) return null;
      return {
        page_url: 'https://spankbang.com' + href,
        preview_url: unesc(rx(c, /data-src="(https?:\/\/[^"]+)"/) || rx(c, /<img[^>]+src="(https?:\/\/[^"]+)"/)),
        title: unesc(rx(c, /title="([^"]+)"/)) || 'SpankBang',
        media_type: 'video',
        tags: 'spankbang'
      };
    }).filter(Boolean),
    resolve: anyStream
  },

  /* ========================== REAL / LEAKS ========================= */
  {
    id: 'coomer', label: 'Coomer (OnlyFans leaks)', group: 'Real / Leaks', enabled: true, kind: 'json',
    note: 'Search works best with a creator handle. Falls back to the user endpoint.',
    headers: { 'User-Agent': UA, 'Accept': 'application/json' },
    url: (q, page) => 'https://coomer.su/api/v1/onlyfans/search?q=' + enc(q) + '&o=' + (page * 50),
    fallbackUrl: (q, page) => 'https://coomer.su/api/v1/onlyfans/user/' + enc(q.trim()) + '?o=' + (page * 50),
    parse: j => {
      const list = (j && (j.posts || j.results)) || (Array.isArray(j) ? j : []);
      return list.map(p => {
        const a = (p.attachments && p.attachments[0]) || {};
        const isVid = /\.(mp4|webm|mov)$/i.test(a.name || '');
        return {
          id: p.id,
          page_url: 'https://coomer.su/onlyfans/user/' + (p.user || '') + '/post/' + p.id,
          file_url: a.path ? 'https://coomer.su/data' + a.path : '',
          preview_url: a.path ? 'https://coomer.su/data' + a.path : '',
          title: (p.user || 'coomer') + ' - ' + (p.title || p.id),
          media_type: isVid ? 'video' : 'image',
          tags: 'coomer onlyfans ' + (p.user || '')
        };
      });
    }
  },
  {
    id: 'kemono', label: 'Kemono (Patreon leaks)', group: 'Real / Leaks', enabled: true, kind: 'json',
    note: 'Same codebase as Coomer, Patreon side.',
    headers: { 'User-Agent': UA, 'Accept': 'application/json' },
    url: (q, page) => 'https://kemono.su/api/v1/patreon/search?q=' + enc(q) + '&o=' + (page * 50),
    fallbackUrl: (q, page) => 'https://kemono.su/api/v1/patreon/user/' + enc(q.trim()) + '?o=' + (page * 50),
    parse: j => {
      const list = (j && (j.posts || j.results)) || (Array.isArray(j) ? j : []);
      return list.map(p => {
        const a = (p.attachments && p.attachments[0]) || {};
        return {
          id: p.id,
          page_url: 'https://kemono.su/patreon/user/' + (p.user || '') + '/post/' + p.id,
          file_url: a.path ? 'https://kemono.su/data' + a.path : '',
          preview_url: a.path ? 'https://kemono.su/data' + a.path : '',
          title: (p.user || 'kemono') + ' - ' + (p.title || p.id),
          media_type: /\.(mp4|webm|mov)$/i.test(a.name || '') ? 'video' : 'image',
          tags: 'kemono patreon ' + (p.user || '')
        };
      });
    }
  },
  {
    id: 'reddit', label: 'Reddit', group: 'Real / Leaks', enabled: true, kind: 'json',
    note: 'Uses raw_json=1 so URLs are not HTML-escaped. Often throttled.',
    headers: { 'User-Agent': UA },
    url: (q, page, cursor) => 'https://www.reddit.com/search.json?q=' + enc(q)
                    + '&include_over_18=on&sort=top&t=all&limit=50&raw_json=1'
                    + (cursor ? '&after=' + enc(cursor) : ''),
    parse: j => {
      const kids = ((j || {}).data || {}).children || [];
      const rows = kids.map(c => {
        const d = c.data || {};
        const media = d.url_overridden_by_dest || d.url || '';
        const prev = (d.preview && d.preview.images && d.preview.images[0]
                      && d.preview.images[0].source && d.preview.images[0].source.url) || '';
        return {
          id: d.id,
          page_url: 'https://reddit.com' + (d.permalink || ''),
          file_url: /\.(mp4|webm|gif)$/i.test(media) ? unesc(media) : '',
          preview_url: unesc(prev),
          title: d.title || 'Reddit post',
          score: d.score || 0,
          media_type: /\.(mp4|webm)$/i.test(media) ? 'video' : 'image',
          tags: 'reddit ' + (d.subreddit || '')
        };
      }).filter(r => r.preview_url || r.file_url);
      rows._cursor = ((j || {}).data || {}).after || '';
      return rows;
    }
  }
];

const VIDEO_SOURCES = [
  { id: 'pornhub', label: 'Pornhub (Lustpress)' },
  { id: 'xvideos', label: 'XVideos (Lustpress)' },
  { id: 'xhamster', label: 'xHamster (Lustpress)' },
  { id: 'xnxx', label: 'XNXX (Lustpress)' },
  { id: 'eporner', label: 'Eporner (Lustpress)' }
];

const LINK_SOURCES = [
  { label: 'Pornhub', url: q => 'https://www.pornhub.com/video/search?search=' + enc(q) },
  { label: 'XVideos', url: q => 'https://www.xvideos.com/?k=' + enc(q) },
  { label: 'xHamster', url: q => 'https://xhamster.com/search/' + enc(q) },
  { label: 'XNXX', url: q => 'https://www.xnxx.com/?k=' + enc(q) },
  { label: 'EPorner', url: q => 'https://www.eporner.com/search/' + enc(q) + '/' },
  { label: 'SpankBang', url: q => 'https://spankbang.com/s/' + enc(q) + '/' },
  { label: 'HQPorner', url: q => 'https://hqporner.com/?q=' + enc(q) },
  { label: 'Noodle Magazine', url: q => 'https://noodlemagazine.com/video/' + enc(q) },
  { label: 'JAVLibrary (by code)', url: q => 'https://www.javlibrary.com/en/vl_searchbyid.php?keyword=' + enc(q) },
  { label: 'JAV site search', url: q => 'https://www.google.com/search?q=' + enc('site:javlibrary.com OR site:jav.guru OR site:javdb.com ' + q) },
  { label: 'IAFD (performers)', url: q => 'https://www.google.com/search?q=' + enc('site:iafd.com ' + q) },
  { label: 'Coomer web search', url: q => 'https://coomer.su/search?q=' + enc(q) },
  { label: 'Kemono web search', url: q => 'https://kemono.su/search?q=' + enc(q) },
  { label: 'X / Twitter media', url: q => 'https://x.com/search?q=' + enc(q + ' filter:media') },
  { label: 'Reddit NSFW', url: q => 'https://www.reddit.com/search/?q=' + enc(q) + '&type=link&include_over_18=on' },
  { label: 'nHentai', url: q => 'https://nhentai.net/search/?q=' + enc(q) },
  { label: 'e-hentai', url: q => 'https://e-hentai.org/?f_search=' + enc(q) },
  { label: 'Hitomi', url: q => 'https://hitomi.la/search.html?q=' + enc(q) },
  { label: 'Hanime', url: q => 'https://hanime.tv/search?q=' + enc(q) },
  { label: 'Sukebei (torrents)', url: q => 'https://sukebei.nyaa.si/?q=' + enc(q) },
  { label: 'F95Zone (games)', url: q => 'https://www.google.com/search?q=' + enc('site:f95zone.to ' + q) },
  { label: 'Fapello', url: q => 'https://fapello.com/search/?q=' + enc(q) }
];
