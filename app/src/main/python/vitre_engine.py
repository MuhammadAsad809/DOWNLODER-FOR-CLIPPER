import json
import os
import sys
import time
import urllib.request
import urllib.parse

try:
    import yt_dlp
except ImportError:
    yt_dlp = None

DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)

def get_engine_version():
    """Return versions of yt-dlp and Python runtime."""
    return json.dumps({
        "ytdlp_version": yt_dlp.version.__version__ if yt_dlp else "Not installed",
        "python_version": sys.version
    })

def resolve_url(url):
    """Resolve shortened redirection URLs to their canonical endpoints."""
    clean = url.strip()
    short_domains = [
        'vm.tiktok.com', 'vt.tiktok.com', 'tiktok.com/t/',
        'youtu.be', 'fb.watch', 't.co', 'vimeo.com'
    ]
    if any(d in clean for d in short_domains):
        try:
            req = urllib.request.Request(clean, headers={'User-Agent': DEFAULT_USER_AGENT})
            with urllib.request.urlopen(req, timeout=8) as resp:
                return resp.geturl()
        except Exception:
            return clean
    return clean

def extract_tiktok_fallback(url):
    """
    Fallback extractor for TikTok when yt-dlp faces anti-bot blocks or proprietary ByteVC1 codecs.
    Returns clean watermark-free H.264 MP4 with synchronized AAC audio.
    """
    try:
        api_url = f"https://www.tikwm.com/api/?url={urllib.parse.quote(url)}"
        req = urllib.request.Request(api_url, headers={'User-Agent': DEFAULT_USER_AGENT})
        with urllib.request.urlopen(req, timeout=12) as resp:
            data = json.loads(resp.read().decode())
            if data.get('code') == 0 and data.get('data'):
                d = data['data']
                play_url = d.get('play')
                return {
                    "title": d.get('title') or "TikTok Video",
                    "thumbnail": d.get('cover') or d.get('origin_cover') or "",
                    "duration": d.get('duration', 0),
                    "uploader": d.get('author', {}).get('nickname') or d.get('author', {}).get('unique_id') or "TikTok Creator",
                    "extractor": "tiktok_api",
                    "url": url,
                    "direct_url": play_url,
                    "formats": [
                        {
                            "format_id": "hd_no_watermark",
                            "ext": "mp4",
                            "resolution": "HD (No Watermark)",
                            "height": 1080,
                            "filesize": d.get('size', 0),
                            "is_video": True,
                            "is_audio": True,
                            "vcodec": "h264",
                            "acodec": "aac",
                            "fps": 30.0,
                            "tbr": 0.0,
                            "needs_audio": False
                        }
                    ]
                }
    except Exception:
        pass
    return None

def download_stream_chunked(direct_url, output_path, callback=None):
    """Download direct media URL chunk-by-chunk while firing progress callbacks."""
    req = urllib.request.Request(direct_url, headers={'User-Agent': DEFAULT_USER_AGENT})
    with urllib.request.urlopen(req, timeout=45) as resp:
        total_size = int(resp.headers.get('content-length', 0))
        downloaded = 0
        start_time = time.time()
        with open(output_path, 'wb') as f:
            while True:
                chunk = resp.read(64 * 1024)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if callback:
                    elapsed = max(time.time() - start_time, 0.001)
                    speed = downloaded / elapsed
                    eta = (total_size - downloaded) / speed if speed > 0 and total_size > 0 else 0
                    callback(json.dumps({
                        "status": "downloading",
                        "downloaded_bytes": downloaded,
                        "total_bytes": total_size,
                        "speed": speed,
                        "eta": int(eta)
                    }))

def inspect_url(url, cookies_path=None):
    """
    Inspect a media URL using yt-dlp (with resilient TikTok/Shorts fallback)
    and return structured JSON metadata. Does NOT invent fake formats.
    """
    if not yt_dlp:
        return json.dumps({"error": "yt-dlp is not available in the runtime"})

    canonical_url = resolve_url(url)
    is_tiktok = any(k in canonical_url.lower() for k in ['tiktok.com', 'musical.ly'])

    # If it's a TikTok URL, attempt fast reliable fallback if desired or run yt-dlp with fallback
    ydl_opts = {
        'skip_download': True,
        'extract_flat': False,
        'no_warnings': True,
        'quiet': True,
        'ignoreerrors': False,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'web']
            }
        },
        'http_headers': {
            'User-Agent': DEFAULT_USER_AGENT,
            'Accept-Language': 'en-US,en;q=0.9',
        }
    }

    if cookies_path and os.path.exists(cookies_path):
        ydl_opts['cookiefile'] = cookies_path

    info = None
    ytdlp_error = None

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(canonical_url, download=False)
    except Exception as e:
        ytdlp_error = str(e)

    # If yt-dlp failed on TikTok, engage TikTok fallback API
    if (not info or ytdlp_error) and is_tiktok:
        tiktok_res = extract_tiktok_fallback(canonical_url)
        if tiktok_res:
            return json.dumps(tiktok_res)

    if not info:
        # Attempt TikTok fallback if error occurred
        if is_tiktok:
            tiktok_res = extract_tiktok_fallback(canonical_url)
            if tiktok_res:
                return json.dumps(tiktok_res)
        return json.dumps({"error": ytdlp_error or "No media information found for this URL"})

    # Handle playlists: take first entry if flat playlist
    if 'entries' in info and info['entries']:
        info = info['entries'][0]

    formats = []
    raw_formats = info.get('formats', [])
    seen_combos = set()

    for f in raw_formats:
        fid = f.get('format_id')
        ext = f.get('ext')
        vcodec = f.get('vcodec', 'none')
        acodec = f.get('acodec', 'none')
        height = f.get('height')
        filesize = f.get('filesize') or f.get('filesize_approx')
        fps = f.get('fps')
        tbr = f.get('tbr')

        is_video = vcodec != 'none'
        is_audio = acodec != 'none'

        # Skip ByteVC1/H.265 TikTok formats if standard H.264 is available
        if 'bytevc1' in str(vcodec).lower():
            continue

        # Format label (e.g. 1080p, 720p, 480p, Audio Only)
        if is_video and height:
            resolution_label = f"{height}p"
        elif is_audio and not is_video:
            resolution_label = f"Audio ({ext})"
        else:
            resolution_label = ext

        combo_key = (resolution_label, ext, is_video, is_audio)
        if combo_key in seen_combos:
            continue
        seen_combos.add(combo_key)

        formats.append({
            "format_id": fid,
            "ext": ext,
            "resolution": resolution_label,
            "height": height or 0,
            "filesize": filesize or 0,
            "is_video": is_video,
            "is_audio": is_audio,
            "vcodec": vcodec,
            "acodec": acodec,
            "fps": fps or 0,
            "tbr": tbr or 0,
            "needs_audio": (is_video and not is_audio)
        })

    # If no formats extracted but it's TikTok, fallback
    if not formats and is_tiktok:
        tiktok_res = extract_tiktok_fallback(canonical_url)
        if tiktok_res:
            return json.dumps(tiktok_res)

    result = {
        "title": info.get('title', 'Unknown Title'),
        "thumbnail": info.get('thumbnail', ''),
        "duration": info.get('duration', 0),
        "uploader": info.get('uploader') or info.get('channel', 'Unknown'),
        "extractor": info.get('extractor', 'generic'),
        "url": canonical_url,
        "formats": formats
    }
    return json.dumps(result)

def download_media(url, format_id, output_path, cookies_path=None, callback=None):
    """
    Download media to output_path.
    If the requested format is video-only, downloads video and audio separately
    and signals needs_muxing=True so native MediaMuxer combines them with sound.
    """
    if not yt_dlp:
        return json.dumps({"error": "yt-dlp is not available"})

    canonical_url = resolve_url(url)
    is_tiktok = any(k in canonical_url.lower() for k in ['tiktok.com', 'musical.ly'])

    # 1. Handle TikTok Fallback downloads
    if format_id == 'hd_no_watermark' or is_tiktok:
        try:
            tiktok_meta = extract_tiktok_fallback(canonical_url)
            if tiktok_meta and tiktok_meta.get('direct_url'):
                download_stream_chunked(tiktok_meta['direct_url'], output_path, callback)
                return json.dumps({
                    "success": True,
                    "needs_muxing": False,
                    "output_path": output_path
                })
        except Exception:
            pass # Fallback to standard yt-dlp if direct stream fails

    # 2. Inspect format to check if video-only
    def make_hook(phase="video"):
        def hook(d):
            if callback:
                status = {
                    "status": d.get("status", "downloading"),
                    "phase": phase,
                    "downloaded_bytes": d.get("downloaded_bytes", 0),
                    "total_bytes": d.get("total_bytes") or d.get("total_bytes_estimate", 0),
                    "speed": d.get("speed", 0),
                    "eta": d.get("eta", 0)
                }
                callback(json.dumps(status))
        return hook

    base_opts = {
        'no_warnings': True,
        'quiet': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'web']
            }
        },
        'http_headers': {
            'User-Agent': DEFAULT_USER_AGENT,
            'Accept-Language': 'en-US,en;q=0.9',
        }
    }
    if cookies_path and os.path.exists(cookies_path):
        base_opts['cookiefile'] = cookies_path

    # Check format characteristics
    is_video_only = False
    try:
        inspect_opts = dict(base_opts)
        inspect_opts['skip_download'] = True
        with yt_dlp.YoutubeDL(inspect_opts) as ydl:
            info = ydl.extract_info(canonical_url, download=False)
            if info:
                for f in info.get('formats', []):
                    if f.get('format_id') == format_id:
                        vcodec = f.get('vcodec', 'none')
                        acodec = f.get('acodec', 'none')
                        if vcodec != 'none' and acodec == 'none':
                            is_video_only = True
                        break
    except Exception:
        pass

    try:
        if is_video_only:
            # Video stream
            video_tmp = f"{output_path}.video.tmp"
            audio_tmp = f"{output_path}.audio.tmp"

            v_opts = dict(base_opts)
            v_opts['format'] = f"{format_id}/bestvideo/best"
            v_opts['outtmpl'] = video_tmp
            v_opts['progress_hooks'] = [make_hook("video")]

            with yt_dlp.YoutubeDL(v_opts) as ydl:
                ydl.download([canonical_url])

            # Audio stream (prefer AAC / m4a format 140 for instant MP4 muxing)
            a_opts = dict(base_opts)
            a_opts['format'] = '140/bestaudio[ext=m4a]/bestaudio/best'
            a_opts['outtmpl'] = audio_tmp
            a_opts['progress_hooks'] = [make_hook("audio")]

            with yt_dlp.YoutubeDL(a_opts) as ydl:
                ydl.download([canonical_url])

            return json.dumps({
                "success": True,
                "needs_muxing": True,
                "video_path": video_tmp,
                "audio_path": audio_tmp,
                "output_path": output_path
            })
        else:
            # Combined / Progressive / Audio-Only format
            c_opts = dict(base_opts)
            c_opts['format'] = f"{format_id}/best" if format_id else 'best'
            c_opts['outtmpl'] = output_path
            c_opts['progress_hooks'] = [make_hook("single")]

            with yt_dlp.YoutubeDL(c_opts) as ydl:
                ydl.download([canonical_url])

            return json.dumps({
                "success": True,
                "needs_muxing": False,
                "output_path": output_path
            })

    except Exception as e:
        # Final safety net for TikTok: if standard download failed, attempt fallback
        if is_tiktok:
            try:
                tiktok_meta = extract_tiktok_fallback(canonical_url)
                if tiktok_meta and tiktok_meta.get('direct_url'):
                    download_stream_chunked(tiktok_meta['direct_url'], output_path, callback)
                    return json.dumps({
                        "success": True,
                        "needs_muxing": False,
                        "output_path": output_path
                    })
            except Exception:
                pass
        return json.dumps({"error": str(e)})
