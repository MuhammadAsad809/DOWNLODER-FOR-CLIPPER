import json
import os
import sys

try:
    import yt_dlp
except ImportError:
    yt_dlp = None

def get_engine_version():
    """Return versions of yt-dlp and Python runtime."""
    return json.dumps({
        "ytdlp_version": yt_dlp.version.__version__ if yt_dlp else "Not installed",
        "python_version": sys.version
    })

def inspect_url(url, cookies_path=None):
    """
    Inspect a media URL using yt-dlp and return structured JSON metadata.
    Does NOT invent or fabricate formats.
    """
    if not yt_dlp:
        return json.dumps({"error": "yt-dlp is not available in the runtime"})

    ydl_opts = {
        'skip_download': True,
        'extract_flat': False,
        'no_warnings': True,
        'quiet': True,
        'ignoreerrors': False,
    }

    if cookies_path and os.path.exists(cookies_path):
        ydl_opts['cookiefile'] = cookies_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return json.dumps({"error": "No media information found for this URL"})

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
                    "tbr": tbr or 0
                })

            result = {
                "title": info.get('title', 'Unknown Title'),
                "thumbnail": info.get('thumbnail', ''),
                "duration": info.get('duration', 0),
                "uploader": info.get('uploader') or info.get('channel', 'Unknown'),
                "extractor": info.get('extractor', 'generic'),
                "url": url,
                "formats": formats
            }
            return json.dumps(result)

    except Exception as e:
        return json.dumps({"error": str(e)})

def download_media(url, format_id, output_path, cookies_path=None, callback=None):
    """
    Download a selected format to output_path.
    Progress is reported via callback(status_dict_json).
    """
    if not yt_dlp:
        return json.dumps({"error": "yt-dlp is not available"})

    def hook(d):
        if callback:
            status = {
                "status": d.get("status"),
                "downloaded_bytes": d.get("downloaded_bytes", 0),
                "total_bytes": d.get("total_bytes") or d.get("total_bytes_estimate", 0),
                "speed": d.get("speed", 0),
                "eta": d.get("eta", 0)
            }
            callback(json.dumps(status))

    ydl_opts = {
        'format': format_id if format_id else 'bestvideo+bestaudio/best',
        'outtmpl': output_path,
        'progress_hooks': [hook],
        'no_warnings': True,
        'quiet': True,
    }

    if cookies_path and os.path.exists(cookies_path):
        ydl_opts['cookiefile'] = cookies_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
            return json.dumps({"success": True, "output_path": output_path})
    except Exception as e:
        return json.dumps({"error": str(e)})
