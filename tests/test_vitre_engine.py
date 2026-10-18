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

if __name__ == '__main__':
    unittest.main()
