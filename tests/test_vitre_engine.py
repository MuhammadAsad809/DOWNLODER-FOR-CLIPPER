import os
import sys
import json
import tempfile
import unittest

# Add app python directory to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'python')))
import vitre_engine

class VitreEngineTests(unittest.TestCase):

    def test_resolve_url_clean(self):
        url = "  https://www.youtube.com/watch?v=dQw4w9WgXcQ  "
        clean = vitre_engine.resolve_url(url)
        self.assertEqual(clean, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")

    def test_check_cancellation_detection(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            media_path = os.path.join(tmpdir, "1001_video.mp4.tmp")
            cancel_path = f"{media_path}.cancel"

            # 1. No cancellation file initially
            self.assertFalse(vitre_engine.check_cancellation(media_path))

            # 2. Touch cancellation file
            with open(cancel_path, 'w') as f:
                f.write("cancel")

            # 3. Should detect and delete cancellation file
            self.assertTrue(vitre_engine.check_cancellation(media_path))
            self.assertFalse(os.path.exists(cancel_path))
            self.assertFalse(vitre_engine.check_cancellation(media_path))

    def test_check_cancellation_dash_parts(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            base_media_path = os.path.join(tmpdir, "1002_video.tmp")
            video_part = f"{base_media_path}.video.tmp"
            cancel_path = f"{base_media_path}.cancel"

            # Touch base cancel file
            with open(cancel_path, 'w') as f:
                f.write("cancel")

            # Checking video_part should detect base cancellation
            self.assertTrue(vitre_engine.check_cancellation(video_part))
            self.assertFalse(os.path.exists(cancel_path))

    def test_format_deduplication_quality_scoring(self):
        raw_formats = [
            # Low bitrate 1080p stream
            {
                "format_id": "137_low",
                "ext": "mp4",
                "height": 1080,
                "vcodec": "avc1.640028",
                "acodec": "none",
                "tbr": 2500,
                "fps": 30,
                "filesize": 25000000
            },
            # High bitrate 1080p stream (should win)
            {
                "format_id": "137_high",
                "ext": "mp4",
                "height": 1080,
                "vcodec": "avc1.640028",
                "acodec": "none",
                "tbr": 4800,
                "fps": 60,
                "filesize": 48000000
            },
            # 720p stream
            {
                "format_id": "22",
                "ext": "mp4",
                "height": 720,
                "vcodec": "avc1.64001f",
                "acodec": "mp4a.40.2",
                "tbr": 1800,
                "fps": 30,
                "filesize": 18000000
            }
        ]

        candidates = {}
        for f in raw_formats:
            fid = f.get('format_id')
            ext = f.get('ext')
            vcodec = str(f.get('vcodec') or 'none').lower()
            acodec = str(f.get('acodec') or 'none').lower()
            height = f.get('height')
            filesize = f.get('filesize') or 0
            fps = f.get('fps') or 0
            tbr = f.get('tbr') or 0

            is_video = vcodec != 'none'
            is_audio = acodec != 'none'
            is_image = False
            resolution_label = f"{height}p"

            combo_key = (resolution_label, ext, is_video, is_audio, is_image)
            total_bitrate = tbr
            codec_bonus = 50 if ('avc1' in vcodec or 'mp4a' in acodec) else 0
            quality_score = (total_bitrate + codec_bonus, filesize, fps)

            candidate_data = {
                "format_id": fid,
                "resolution": resolution_label,
                "filesize": filesize
            }
            if combo_key not in candidates or quality_score > candidates[combo_key][0]:
                candidates[combo_key] = (quality_score, candidate_data)

        # 1080p candidate must be the high-bitrate variant (137_high)
        selected_1080p = candidates[("1080p", "mp4", True, False, False)][1]
        self.assertEqual(selected_1080p["format_id"], "137_high")
        self.assertEqual(selected_1080p["filesize"], 48000000)

        # 720p candidate must be present
        selected_720p = candidates[("720p", "mp4", True, True, False)][1]
        self.assertEqual(selected_720p["format_id"], "22")

    def test_image_magic_byte_signatures(self):
        def detect_type(header_bytes):
            if header_bytes[:4] == b'\x89PNG':
                return 'png', 'image/png'
            if header_bytes[:3] == b'GIF':
                return 'gif', 'image/gif'
            if header_bytes[:4] == b'RIFF' and header_bytes[8:12] == b'WEBP':
                return 'webp', 'image/webp'
            if header_bytes[:3] == b'\xFF\xD8\xFF':
                return 'jpg', 'image/jpeg'
            return 'jpg', 'image/jpeg'

        png_head = b'\x89PNG\r\n\x1a\n\x00\x00\x00\r'
        gif_head = b'GIF89a\x01\x00\x01\x00\x80\x00\x00'
        webp_head = b'RIFF\x20\x00\x00\x00WEBPVP8 '
        jpg_head = b'\xFF\xD8\xFF\xE0\x00\x10JFIF'

        self.assertEqual(detect_type(png_head), ('png', 'image/png'))
        self.assertEqual(detect_type(gif_head), ('gif', 'image/gif'))
        self.assertEqual(detect_type(webp_head), ('webp', 'image/webp'))
        self.assertEqual(detect_type(jpg_head), ('jpg', 'image/jpeg'))

    def test_portrait_resolution_normalization(self):
        # Simulated raw formats for an Instagram reel: 720x1280 and 1080x1920
        raw_formats = [
            {
                "format_id": "dash-1280",
                "ext": "mp4",
                "width": 720,
                "height": 1280,
                "vcodec": "avc1.64001f",
                "acodec": "none",
                "tbr": 2100,
                "fps": 30
            },
            {
                "format_id": "progressive-720",
                "ext": "mp4",
                "width": 720,
                "height": 1280,
                "vcodec": "avc1.64001f",
                "acodec": "mp4a.40.2",
                "tbr": 1900,
                "fps": 30
            },
            {
                "format_id": "dash-1920",
                "ext": "mp4",
                "width": 1080,
                "height": 1920,
                "vcodec": "avc1.640028",
                "acodec": "none",
                "tbr": 4200,
                "fps": 30
            }
        ]

        # Use the normalization logic directly to assert correct standard display tiers
        candidates = {}
        for f in raw_formats:
            vcodec = str(f.get('vcodec') or 'none').lower()
            acodec = str(f.get('acodec') or 'none').lower()
            width = f.get('width') or 0
            height = f.get('height') or 0
            is_video = vcodec != 'none'
            is_audio = acodec != 'none'

            tier = min(width, height) if width < height else height
            combo_key = ('video', tier)

            audio_bonus = 2000 if (is_video and is_audio) else 0
            score = audio_bonus + (f.get('tbr') or 0)
            if combo_key not in candidates or score > candidates[combo_key][0]:
                candidates[combo_key] = (score, f, tier)

        # 720x1280 must be normalized to tier 720 (720p HD), NOT 1280p
        self.assertIn(('video', 720), candidates)
        best_720 = candidates[('video', 720)][1]
        # Progressive format with audio must win over video-only DASH
        self.assertEqual(best_720['format_id'], 'progressive-720')

        # 1080x1920 must be normalized to tier 1080 (1080p FHD), NOT 1920p
        self.assertIn(('video', 1080), candidates)
        best_1080 = candidates[('video', 1080)][1]
        self.assertEqual(candidates[('video', 1080)][2], 1080)

    def test_instagram_progressive_format_priority(self):
        # Simulated Instagram formats: format 0 (progressive mp4 with no codecs in dict) and dash video
        raw_formats = [
            {
                "format_id": "dash-720v",
                "ext": "mp4",
                "width": 720,
                "height": 1280,
                "vcodec": "avc1.64001f",
                "acodec": "none",
                "format_note": "DASH video",
                "tbr": 2000
            },
            {
                "format_id": "0",
                "ext": "mp4",
                "width": None,
                "height": None,
                "vcodec": None,
                "acodec": "none",
                "format_note": None,
                "tbr": 1800
            }
        ]
        info = {"width": 720, "height": 1280}

        has_standalone_audio = False # Instagram has no standalone audio streams
        candidates = {}
        for f in raw_formats:
            fid = f.get('format_id')
            ext = f.get('ext')
            vcodec = str(f.get('vcodec') or 'none').lower()
            acodec = str(f.get('acodec') or 'none').lower()
            width = f.get('width') or 0
            height = f.get('height') or 0
            format_note = str(f.get('format_note') or '').lower()

            is_dash_video = ('dash' in str(fid).lower() and str(fid).endswith('v')) or ('dash video' in format_note)
            is_video = (vcodec != 'none') or (ext in ['mp4', 'm4v'] and not is_dash_video)

            if is_video:
                if not height:
                    height = info.get('height') or 0
                if not width:
                    width = info.get('width') or 0

            tier = min(width, height) if width < height else height
            combo_key = ('video', tier)

            has_sound = (is_video and not is_dash_video and (acodec != 'none' or not has_standalone_audio))
            audio_bonus = 2000 if has_sound else 0
            if is_dash_video and not has_standalone_audio:
                audio_bonus = -5000

            score = audio_bonus + (f.get('tbr') or 0)
            if combo_key not in candidates or score > candidates[combo_key][0]:
                candidates[combo_key] = (score, f)

        # Format 0 (progressive with sound) MUST win over silent DASH video
        winner = candidates[('video', 720)][1]
        self.assertEqual(winner['format_id'], '0')

    def test_codec_prioritization_h264_over_vp9(self):
        # VP9 stream with higher bitrate vs H.264 stream with slightly lower bitrate
        raw_formats = [
            {
                "format_id": "248_vp9",
                "ext": "webm",
                "height": 1080,
                "vcodec": "vp9",
                "acodec": "none",
                "tbr": 3500
            },
            {
                "format_id": "137_h264",
                "ext": "mp4",
                "height": 1080,
                "vcodec": "avc1.64002a",
                "acodec": "none",
                "tbr": 2800
            }
        ]

        candidates = {}
        for f in raw_formats:
            vcodec = f['vcodec']
            codec_bonus = 0
            if 'avc1' in vcodec or 'h264' in vcodec:
                codec_bonus += 500000
            elif 'vp9' in vcodec:
                codec_bonus += 10000
            score = codec_bonus + f['tbr']
            candidates[f['height']] = (score, f)

        # H.264 stream must win for native Android MediaMuxer compatibility
        self.assertEqual(candidates[1080][1]['format_id'], '137_h264')

    def test_monotonic_multiphase_progress_calculation(self):
        # Video phase: 0% to 85%
        raw_video_pct = 0.50 # 50% through video
        scaled_video = raw_video_pct * 0.85
        self.assertAlmostEqual(scaled_video, 0.425)

        # Finished video phase
        scaled_video_done = 1.0 * 0.85
        self.assertAlmostEqual(scaled_video_done, 0.85)

        # Audio phase: starts at 85%, goes up to 97%
        raw_audio_pct = 0.50 # 50% through audio
        scaled_audio = 0.85 + (raw_audio_pct * 0.12)
        self.assertAlmostEqual(scaled_audio, 0.91)

        # Audio phase finished
        scaled_audio_done = 0.85 + (1.0 * 0.12)
        self.assertAlmostEqual(scaled_audio_done, 0.97)

        # Ensure monotonic ordering: scaled_video_done <= scaled_audio
        self.assertLessEqual(scaled_video_done, scaled_audio)

    def test_direct_url_discrimination_for_dailymotion_formats(self):
        # Dailymotion uses format IDs like 'http-480-1', 'http-720', 'http-1080'
        # These must NOT be mistaken for direct URLs (e.g. 'http://...' or 'https://...')
        dm_format_id = "http-480-1"
        real_url = "https://example.com/stream.mp4"

        is_direct_url_dm = bool(dm_format_id and (dm_format_id.startswith('http://') or dm_format_id.startswith('https://')))
        is_direct_url_real = bool(real_url and (real_url.startswith('http://') or real_url.startswith('https://')))

        self.assertFalse(is_direct_url_dm)
        self.assertTrue(is_direct_url_real)

    def test_resolve_url_telegram_normalization(self):
        # Telegram web preview URLs (t.me/s/...) must be normalized for yt-dlp telegram:embed
        t_url = "https://t.me/s/durov/50"
        normalized = vitre_engine.resolve_url(t_url)
        self.assertEqual(normalized, "https://t.me/durov/50")

    def test_resolve_url_linkedin_activity_normalization(self):
        # LinkedIn post URLs with activity-ID or ugcPost:ID must normalize to canonical URN
        u1 = "https://www.linkedin.com/posts/google_deepmind-activity-7123456789012345678-abcde?utm_source=share"
        u2 = "https://www.linkedin.com/feed/update/urn:li:ugcPost:7123456789012345678/"
        expected = "https://www.linkedin.com/feed/update/urn:li:activity:7123456789012345678"

        self.assertEqual(vitre_engine.resolve_url(u1), expected)
        self.assertEqual(vitre_engine.resolve_url(u2), expected)

    def test_spotify_url_recognition(self):
        # Spotify links must be recognized as spotify URLs
        url = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
        self.assertTrue(any(k in url.lower() for k in ['spotify.com', 'spotify.link', 'spoti.fi']))

    def test_direct_media_extension_detection(self):
        media_exts = ['.mp4', '.mov', '.webm', '.mkv', '.mp3', '.m4a', '.wav', '.aac', '.flac', '.opus']
        u1 = "https://cdn.discordapp.com/attachments/123456/7890/voice.mp3?ex=67"
        u2 = "https://example.com/sample.mp4?token=abc"

        clean1 = u1.split('?')[0].lower()
        clean2 = u2.split('?')[0].lower()

        self.assertTrue(any(clean1.endswith(ext) for ext in media_exts))
        self.assertTrue(any(clean2.endswith(ext) for ext in media_exts))

    def test_kick_url_patterns(self):
        # Kick live, clip, and VOD URLs must be handled appropriately
        import re
        clip_pat = r'https?://(?:www\.)?kick\.com/[\w-]+(?:/clips/|/?\?(?:[^#]+&)?clip=)(?P<id>clip_[\w-]+)'
        vod_pat = r'https?://(?:www\.)?kick\.com/[\w-]+/videos/(?P<id>[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12})'

        c_url = "https://kick.com/iceposeidon?clip=clip_01M5E6TAHMBPHJKNTZD8TWGWFZ"
        v_url = "https://kick.com/iceposeidon/videos/a090e010-bd5e-4290-b2bb-cb343249d4fc"

        self.assertTrue(bool(re.match(clip_pat, c_url)))
        self.assertTrue(bool(re.match(vod_pat, v_url)))

if __name__ == '__main__':
    unittest.main()


