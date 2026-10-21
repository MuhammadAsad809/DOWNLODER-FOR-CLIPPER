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

def check_cancellation(output_path):
    """Check if cancellation signal file exists."""
    candidates = [f"{output_path}.cancel"]
    if output_path.endswith('.video.tmp') or output_path.endswith('.audio.tmp'):
        base = output_path.rsplit('.', 2)[0] + '.tmp'
        candidates.append(f"{base}.cancel")
        candidates.append(f"{output_path.rsplit('.', 2)[0]}.cancel")

    for c in candidates:
        if os.path.exists(c):
            try:
                os.remove(c)
            except Exception:
                pass
            return True
    return False

def extract_tiktok_fallback(url):
    """
    Fallback extractor using TikWM API when direct yt-dlp extraction encounters anti-bot
    blocks or login walls.
    """
    try:
        api_url = f"https://www.tikwm.com/api/?url={urllib.parse.quote(url)}"
        req = urllib.request.Request(api_url, headers={'User-Agent': DEFAULT_USER_AGENT})
        with urllib.request.urlopen(req, timeout=12) as resp:
            data = json.loads(resp.read().decode())
            if data.get('code') == 0 and data.get('data'):
                d = data['data']
                play_url = d.get('play')
                height = d.get('height') or 0
                res_label = f"{height}p (Watermark-Free)" if height > 0 else "HD (Watermark-Free)"
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
                            "resolution": res_label,
                            "height": height,
                            "filesize": d.get('size', 0),
                            "is_video": True,
                            "is_audio": True,
                            "vcodec": "h264",
                            "acodec": "aac",
                            "fps": 0.0,
                            "tbr": 0.0,
                            "needs_audio": False
                        }
                    ]
                }
    except Exception:
        pass
    return None

def download_stream_chunked(direct_url, output_path, callback=None):
    """Download direct media URL chunk-by-chunk with cooperative cancellation check."""
    req = urllib.request.Request(direct_url, headers={'User-Agent': DEFAULT_USER_AGENT})
    with urllib.request.urlopen(req, timeout=45) as resp:
        total_size = int(resp.headers.get('content-length', 0))
        downloaded = 0
        start_time = time.time()
        with open(output_path, 'wb') as f:
            while True:
                if check_cancellation(output_path):
                    raise InterruptedError("Download cancelled by user")
                chunk = resp.read(64 * 1024)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if callback:
                    elapsed = max(time.time() - start_time, 0.001)
                    speed = downloaded / elapsed
                    eta = (total_size - downloaded) / speed if speed > 0 and total_size > 0 else 0
                    pct = (downloaded / total_size) if total_size > 0 else 0.0
                    callback(json.dumps({
                        "status": "downloading",
                        "phase": "single",
                        "downloaded_bytes": downloaded,
                        "total_bytes": total_size,
                        "percentage": min(1.0, max(0.0, pct)),
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

    # Direct image detection fallback (Pinterest, i.redd.it, Twitter images, Unsplash, direct jpg/png)
    if not info:
        url_lower = canonical_url.lower().split('?')[0]
        image_exts = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.bmp', '.svg']
        is_image_url = any(url_lower.endswith(ext) for ext in image_exts) or \
                       any(dom in canonical_url.lower() for dom in ['pinimg.com', 'i.redd.it', 'images.unsplash.com', 'imgur.com', 'pbs.twimg.com'])

        if is_image_url:
            try:
                img_req = urllib.request.Request(canonical_url, headers={'User-Agent': DEFAULT_USER_AGENT})
                with urllib.request.urlopen(img_req, timeout=10) as resp:
                    c_type = resp.headers.get('content-type', '').lower()
                    c_len = int(resp.headers.get('content-length', 0))
                    if 'image' in c_type or is_image_url:
                        ext = 'jpg'
                        for candidate in ['png', 'webp', 'gif', 'jpeg', 'jpg']:
                            if candidate in c_type or url_lower.endswith(f'.{candidate}'):
                                ext = candidate
                                break
                        return json.dumps({
                            "title": "Photo / Image Asset",
                            "thumbnail": canonical_url,
                            "duration": 0,
                            "uploader": "Web Image",
                            "extractor": "image_direct",
                            "url": canonical_url,
                            "is_image_post": True,
                            "formats": [{
                                "format_id": "direct_image",
                                "ext": ext,
                                "resolution": "Original Image",
                                "height": 0,
                                "filesize": c_len,
                                "is_video": False,
                                "is_audio": False,
                                "is_image": True,
                                "vcodec": "none",
                                "acodec": "none",
                                "fps": 0,
                                "tbr": 0,
                                "needs_audio": False,
                                "direct_url": canonical_url
                            }]
                        })
            except Exception:
                pass

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
    candidates = {}
    has_standalone_audio = any(
        (str(f.get('vcodec') or 'none').lower() == 'none' and str(f.get('acodec') or 'none').lower() != 'none')
        for f in raw_formats
    )

    for f in raw_formats:
        fid = f.get('format_id')
        ext = f.get('ext')
        vcodec = str(f.get('vcodec') or 'none').lower()
        acodec = str(f.get('acodec') or 'none').lower()
        width = f.get('width') or 0
        height = f.get('height') or 0
        filesize = f.get('filesize') or f.get('filesize_approx') or 0
        fps = f.get('fps') or 0
        tbr = f.get('tbr') or 0
        vbr = f.get('vbr') or 0
        abr = f.get('abr') or 0
        format_note = str(f.get('format_note') or '').lower()

        is_audio = (acodec != 'none') and (vcodec == 'none' or 'audio' in str(fid).lower())
        is_image = (ext in ['jpg', 'jpeg', 'png', 'webp', 'gif'] or 'image' in str(fid).lower()) and not is_audio
        if is_image and (not ext or ext in ['unknown_video', 'none', 'mp4']):
            ext = 'jpg'

        # Detect video formats even when vcodec is unspecified by CDN (e.g. Instagram progressive mp4s, Reddit, Twitter)
        is_dash_video = ('dash' in str(fid).lower() and str(fid).endswith('v')) or ('dash video' in format_note)
        is_video = (vcodec != 'none') or (ext in ['mp4', 'm4v', 'webm', 'mkv', 'mov', '3gp', 'flv'] and not is_audio and not is_image)

        # Fallback to container/post dimensions when individual stream metadata lacks height/width
        if is_video:
            if not height or height == 0:
                height = info.get('height') or 0
            if not width or width == 0:
                width = info.get('width') or 0

        # Skip ByteVC1/H.265 TikTok formats if standard H.264 is available
        if 'bytevc1' in vcodec:
            continue

        # Format label with vertical video normalization (e.g. 1080x1920 -> 1080p Full HD, 720x1280 -> 720p HD)
        tier = 0
        if is_video:
            if width > 0 and height > 0:
                tier = min(width, height) if width < height else height
            elif height in (1920, 1080):
                tier = 1080
            elif height in (1280, 720):
                tier = 720
            elif height in (854, 480):
                tier = 480
            elif height in (640, 360):
                tier = 360
            else:
                tier = height or 0

            if tier >= 2160:
                resolution_label = f"{tier}p (4K UHD)"
            elif tier >= 1440:
                resolution_label = f"{tier}p (2K QHD)"
            elif tier >= 1080:
                resolution_label = f"{tier}p (Full HD)"
            elif tier >= 720:
                resolution_label = f"{tier}p (HD)"
            elif tier > 0:
                resolution_label = f"{tier}p"
            else:
                resolution_label = "Video"
            combo_key = ('video', tier)
        elif is_audio and not is_video:
            resolution_label = f"Audio ({ext})"
            combo_key = ('audio', ext)
        elif is_image:
            resolution_label = f"Image ({ext.upper()})"
            combo_key = ('image', ext)
        else:
            resolution_label = ext
            combo_key = (resolution_label, ext)

        # Progressive videos with audio (e.g. Instagram format 0, standard mp4) or formats with audio track
        has_sound = (is_video and not is_dash_video and (acodec != 'none' or not has_standalone_audio)) or is_audio
        audio_bonus = 2000000 if has_sound else 0
        # Heavily penalize silent DASH video streams if there are no standalone audio streams to mux with
        if is_dash_video and not has_standalone_audio:
            audio_bonus = -5000000

        # Heavily prioritize H.264/AVC video and AAC audio for guaranteed Android MediaMuxer hardware compatibility
        codec_bonus = 0
        if 'avc1' in vcodec or 'h264' in vcodec:
            codec_bonus += 500000
        elif 'hev1' in vcodec or 'hvc1' in vcodec:
            codec_bonus += 100000
        elif 'vp9' in vcodec or 'vp09' in vcodec:
            codec_bonus += 10000
        if 'mp4a' in acodec or 'aac' in acodec:
            codec_bonus += 500000

        total_bitrate = tbr or (vbr + abr)
        quality_score = (audio_bonus + codec_bonus + total_bitrate, filesize, fps)

        is_social_platform = any(k in canonical_url.lower() for k in [
            'instagram.com', 'instagr.am', 'tiktok.com', 'twitter.com', 'x.com', 'facebook.com', 'fb.watch'
        ])

        candidate_data = {
            "format_id": fid,
            "ext": ext,
            "resolution": resolution_label,
            "height": tier if is_video else (height or 0),
            "filesize": filesize or 0,
            "is_video": is_video,
            "is_audio": is_audio,
            "is_image": is_image,
            "vcodec": vcodec,
            "acodec": acodec,
            "fps": fps or 0,
            "tbr": tbr or 0,
            "needs_audio": False if is_social_platform else (is_video and not has_sound and has_standalone_audio),
            "direct_url": f.get('url') or ''
        }

        if combo_key not in candidates or quality_score > candidates[combo_key][0]:
            candidates[combo_key] = (quality_score, candidate_data)

    formats = [item[1] for item in candidates.values()]

    # Fallback for direct image posts (Pinterest, Unsplash, Instagram photos)
    if not formats and (info.get('url') or info.get('thumbnail')):
        direct_img = info.get('url') or info.get('thumbnail')
        img_ext = info.get('ext') or 'jpg'
        if img_ext in ['unknown_video', 'none', 'mp4']:
            img_ext = 'jpg'
        formats.append({
            "format_id": "direct_image",
            "ext": img_ext,
            "resolution": "Original Image",
            "height": info.get('height') or 0,
            "filesize": info.get('filesize') or 0,
            "is_video": False,
            "is_audio": False,
            "is_image": True,
            "vcodec": "none",
            "acodec": "none",
            "fps": 0,
            "tbr": 0,
            "needs_audio": False,
            "direct_url": direct_img
        })

    # Sort formats: Video (highest resolution first), then Images (largest first), then Audio
    def format_sort_key(item):
        cat = 0 if item.get('is_video') else (1 if item.get('is_image') else 2)
        h = item.get('height') or 0
        sz = item.get('filesize') or 0
        return (cat, -h, -sz)

    formats.sort(key=format_sort_key)

    # If no formats extracted but it's TikTok, fallback
    if not formats and is_tiktok:
        tiktok_res = extract_tiktok_fallback(canonical_url)
        if tiktok_res:
            return json.dumps(tiktok_res)

    is_image_post = any(f.get('is_image') for f in formats) and not any(f.get('is_video') for f in formats)

    result = {
        "title": info.get('title', 'Unknown Title'),
        "thumbnail": info.get('thumbnail', ''),
        "duration": info.get('duration', 0),
        "uploader": info.get('uploader') or info.get('channel', 'Unknown'),
        "extractor": info.get('extractor', 'generic'),
        "url": canonical_url,
        "is_image_post": is_image_post,
        "formats": formats
    }
    return json.dumps(result)

def download_media(url, format_id, output_path, cookies_path=None, callback=None, clip_start=None, clip_end=None, is_audio_only=False, audio_format='mp3'):
    """
    Download media to output_path.
    If the requested format is video-only, downloads video and audio separately
    and signals needs_muxing=True so native MediaMuxer combines them with sound.
    Supports direct audio-only stream extraction and time clipping.
    """
    if not yt_dlp:
        return json.dumps({"error": "yt-dlp is not available"})

    canonical_url = resolve_url(url)
    is_tiktok = any(k in canonical_url.lower() for k in ['tiktok.com', 'musical.ly'])

    # 1. Handle Direct Image / Image Post downloads
    is_direct_url = bool(format_id and (format_id.startswith('http://') or format_id.startswith('https://')))
    if format_id == 'direct_image' or is_direct_url:
        direct_target = format_id if is_direct_url else None
        if not direct_target:
            try:
                inspect_opts = {
                    'no_warnings': True,
                    'quiet': True,
                    'skip_download': True,
                    'http_headers': {'User-Agent': DEFAULT_USER_AGENT}
                }
                if cookies_path and os.path.exists(cookies_path):
                    inspect_opts['cookiefile'] = cookies_path
                with yt_dlp.YoutubeDL(inspect_opts) as ydl:
                    info = ydl.extract_info(canonical_url, download=False)
                    direct_target = info.get('url') or info.get('thumbnail')
            except Exception:
                pass
        if direct_target:
            download_stream_chunked(direct_target, output_path, callback)
            return json.dumps({
                "success": True,
                "needs_muxing": False,
                "output_path": output_path
            })

    # 2. Handle TikTok Fallback downloads only if explicitly requested
    if format_id == 'hd_no_watermark':
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

    # 3. Progress hook with cooperative cancellation and monotonic multi-phase scaling
    def make_hook(phase="single"):
        def hook(d):
            if check_cancellation(output_path):
                raise InterruptedError("Download cancelled by user")
            if callback:
                dl = d.get("downloaded_bytes", 0)
                tot = d.get("total_bytes") or d.get("total_bytes_estimate", 0)
                raw_pct = (dl / tot) if (tot and tot > 0) else 0.0

                if phase == "dash_video":
                    pct = raw_pct * 0.85
                elif phase == "dash_audio":
                    pct = 0.85 + (raw_pct * 0.12)
                else:
                    pct = raw_pct

                status = {
                    "status": d.get("status", "downloading"),
                    "phase": phase,
                    "downloaded_bytes": dl,
                    "total_bytes": tot,
                    "percentage": min(1.0, max(0.0, pct)),
                    "speed": d.get("speed", 0),
                    "eta": d.get("eta", 0)
                }
                callback(json.dumps(status))
        return hook

    base_opts = {
        'no_warnings': True,
        'quiet': True,
        'http_headers': {
            'User-Agent': DEFAULT_USER_AGENT,
            'Accept-Language': 'en-US,en;q=0.9',
        }
    }
    if cookies_path and os.path.exists(cookies_path):
        base_opts['cookiefile'] = cookies_path

    # Configure partial download range if clipping requested and ffmpeg is installed
    if clip_start is not None and clip_end is not None:
        try:
            import shutil
            if shutil.which('ffmpeg'):
                def range_selector(info_dict, ydl):
                    return [{'start_time': float(clip_start), 'end_time': float(clip_end), 'title': 'clip'}]
                base_opts['download_ranges'] = range_selector
                base_opts['force_keyframes_at_cuts'] = False
        except Exception:
            pass

    try:
        # 4. Direct Audio-Only download branch
        if is_audio_only:
            a_opts = dict(base_opts)
            if audio_format in ['m4a', 'aac']:
                a_opts['format'] = f"{format_id}/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best" if format_id and format_id not in ['best', 'audio_only', 'bestaudio'] else 'bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best'
            elif audio_format in ['opus', 'ogg', 'webm']:
                a_opts['format'] = f"{format_id}/bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best" if format_id and format_id not in ['best', 'audio_only', 'bestaudio'] else 'bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best'
            else:
                a_opts['format'] = f"{format_id}/bestaudio/best" if format_id and format_id not in ['best', 'audio_only', 'bestaudio'] else 'bestaudio/best'

            a_opts['outtmpl'] = output_path
            a_opts['progress_hooks'] = [make_hook("audio")]

            with yt_dlp.YoutubeDL(a_opts) as ydl:
                ydl.download([canonical_url])

            return json.dumps({
                "success": True,
                "needs_muxing": False,
                "output_path": output_path
            })

        # Check format characteristics with robust string conversion
        is_video_only = False
        has_standalone_audio = False
        try:
            inspect_opts = dict(base_opts)
            inspect_opts['skip_download'] = True
            with yt_dlp.YoutubeDL(inspect_opts) as ydl:
                info = ydl.extract_info(canonical_url, download=False)
                if info:
                    for f in info.get('formats', []):
                        v_c = str(f.get('vcodec') or 'none').lower()
                        a_c = str(f.get('acodec') or 'none').lower()
                        f_id = str(f.get('format_id'))
                        f_note = str(f.get('format_note') or '').lower()
                        is_dash_v = ('dash' in f_id.lower() and f_id.endswith('v')) or ('dash video' in f_note)

                        if v_c == 'none' and a_c != 'none':
                            has_standalone_audio = True
                        if f_id == str(format_id):
                            if (v_c != 'none' and a_c == 'none') or is_dash_v:
                                is_video_only = True
        except Exception:
            pass

        # Social platforms (Instagram, TikTok, Facebook, Twitter) serve progressive MP4s
        # with video and audio already combined on the CDN. They must never be split or muxed.
        is_social_platform = any(k in canonical_url.lower() for k in [
            'instagram.com', 'instagr.am', 'tiktok.com', 'twitter.com', 'x.com', 'facebook.com', 'fb.watch'
        ])

        # Only attempt separate video + audio download if standalone audio stream actually exists AND not a social progressive platform
        if is_video_only and has_standalone_audio and not is_social_platform:
            video_tmp = f"{output_path}.video.tmp"
            audio_tmp = f"{output_path}.audio.tmp"

            v_opts = dict(base_opts)
            v_opts['format'] = f"{format_id}/bestvideo[ext=mp4]/bestvideo/best"
            v_opts['outtmpl'] = video_tmp
            v_opts['progress_hooks'] = [make_hook("dash_video")]

            with yt_dlp.YoutubeDL(v_opts) as ydl:
                ydl.download([canonical_url])

            # Audio stream (prefer AAC / m4a for instant MP4 muxing)
            a_opts = dict(base_opts)
            a_opts['format'] = 'bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best'
            a_opts['outtmpl'] = audio_tmp
            a_opts['progress_hooks'] = [make_hook("dash_audio")]

            audio_download_ok = False
            try:
                with yt_dlp.YoutubeDL(a_opts) as ydl:
                    ydl.download([canonical_url])
                if os.path.exists(audio_tmp) and os.path.getsize(audio_tmp) > 512:
                    audio_download_ok = True
            except Exception:
                audio_download_ok = False

            if audio_download_ok:
                return json.dumps({
                    "success": True,
                    "needs_muxing": True,
                    "video_path": video_tmp,
                    "audio_path": audio_tmp,
                    "output_path": output_path
                })
            else:
                # If audio failed, clean up video tmp and fallback to progressive combined download
                try:
                    if os.path.exists(video_tmp):
                        os.remove(video_tmp)
                    if os.path.exists(audio_tmp):
                        os.remove(audio_tmp)
                except Exception:
                    pass

        # Combined / Progressive format (preferred for Instagram, TikTok, Facebook, Twitter)
        c_opts = dict(base_opts)
        if is_social_platform:
            # On Instagram and social platforms, download the full progressive MP4 with audio
            c_opts['format'] = f"{format_id}/best[ext=mp4]/best" if (format_id and not format_id.startswith('dash')) else 'best[ext=mp4]/best'
        elif is_video_only and not has_standalone_audio:
            c_opts['format'] = 'best[ext=mp4]/best'
        else:
            c_opts['format'] = f"{format_id}/best[ext=mp4]/best" if format_id else 'best[ext=mp4]/best'
        c_opts['outtmpl'] = output_path
        c_opts['progress_hooks'] = [make_hook("single")]

        with yt_dlp.YoutubeDL(c_opts) as ydl:
            ydl.download([canonical_url])

            # Validate output file
            if os.path.exists(output_path):
                size = os.path.getsize(output_path)
                if size < 1024:
                    with open(output_path, 'rb') as vf:
                        head = vf.read(256)
                        if b'<html' in head.lower() or b'<!doctype' in head.lower():
                            return json.dumps({"error": "Downloaded file is an HTML login/error page, not valid media."})

            return json.dumps({
                "success": True,
                "needs_muxing": False,
                "output_path": output_path
            })

    except Exception as e:
        if isinstance(e, InterruptedError) or "cancelled" in str(e).lower():
            return json.dumps({"error": "Download cancelled by user", "cancelled": True})
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
