import os
import sys
import unittest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTHON_ROOT = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "python")
PLUGINS_ROOT = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "assets", "plugins")

sys.path.insert(0, PYTHON_ROOT)
sys.path.insert(0, PLUGINS_ROOT)

from extera_utils.metadata_parser import read_metadata
import fake_account_studio


class FakeAccountStudioTest(unittest.TestCase):

    def test_metadata(self):
        plugin_file = os.path.join(PLUGINS_ROOT, "fake_account_studio.py")
        meta = read_metadata(plugin_file)
        self.assertEqual(meta["id"], "fake_account_studio")
        self.assertEqual(meta["name"], "Fake Account & Demo Studio")
        self.assertEqual(meta["version"], "1.0.0")
        self.assertIn("hooks", meta["permissions"])
        self.assertIn("settings", meta["permissions"])
        self.assertIn("ui", meta["permissions"])

    def test_plugin_instantiation_and_settings(self):
        plugin = fake_account_studio.FakeAccountStudioPlugin()
        plugin._exteraless_attach("fake_account_studio")

        # Verify English defaults
        self.assertTrue(plugin.is_fake_account_active())
        self.assertTrue(plugin.is_fake_premium())
        self.assertTrue(plugin.is_fake_verified())
        # hide_real_chats is False by default so actual chats are kept intact alongside demo chats
        self.assertFalse(plugin.is_hide_real_chats())
        self.assertTrue(plugin.is_demo_chats_active())
        self.assertEqual(plugin.get_fake_first_name(), "Alex")
        self.assertEqual(plugin.get_fake_last_name(), "Smith")
        self.assertEqual(plugin.get_fake_username(), "yumi_creator")

        # Test settings creation
        settings = plugin.create_settings()
        self.assertIsInstance(settings, list)
        self.assertGreater(len(settings), 20)

        # Check presets
        plugin._apply_preset("durov")
        self.assertEqual(plugin.get_fake_first_name(), "Pavel")
        self.assertEqual(plugin.get_fake_last_name(), "Durov")
        self.assertEqual(plugin.get_fake_username(), "durov")
        self.assertTrue(plugin.is_fake_premium())
        self.assertTrue(plugin.is_fake_verified())

        plugin._apply_preset("anon")
        self.assertEqual(plugin.get_fake_first_name(), "Anonymous")
        self.assertFalse(plugin.is_fake_premium())
        self.assertFalse(plugin.is_fake_verified())

    def test_demo_chats_data_and_history(self):
        plugin = fake_account_studio.FakeAccountStudioPlugin()
        plugin._init_demo_data()

        # Check demo peers
        self.assertTrue(plugin.is_demo_dialog(99990001))
        self.assertTrue(plugin.is_demo_dialog(99990002))
        self.assertTrue(plugin.is_demo_dialog(-99990003))

        history = plugin._chat_histories.get(99990001)
        self.assertIsNotNone(history)
        self.assertGreater(len(history), 0)

        # Simulate sending a message to demo chat
        initial_count = len(history)
        class DummyParams:
            peer = 99990001
            message = "Test message for ad video"

        res = plugin.on_send_message_hook(0, DummyParams())
        self.assertEqual(res.strategy, "CANCEL")
        self.assertEqual(len(plugin._chat_histories[99990001]), initial_count + 1)
        self.assertEqual(plugin._chat_histories[99990001][-1]["text"], "Test message for ad video")
        self.assertTrue(plugin._chat_histories[99990001][-1]["out"])

    def test_custom_chat_creation_keeps_existing(self):
        plugin = fake_account_studio.FakeAccountStudioPlugin()
        plugin._exteraless_attach("fake_account_studio")
        plugin._init_demo_data()

        initial_count = len(plugin._chat_histories)
        plugin.set_setting("new_chat_title", "Crypto Sponsor")
        plugin.set_setting("new_chat_message", "Partnership agreement approved! 💎")
        plugin.set_setting("new_chat_is_channel", False)

        plugin._add_custom_chat_clicked()
        self.assertEqual(len(plugin._chat_histories), initial_count + 1)

        # Check that the new chat exists
        new_peer_id = list(plugin._chat_histories.keys())[-1]
        self.assertTrue(plugin.is_demo_dialog(new_peer_id))
        self.assertEqual(fake_account_studio.DEMO_PEERS[new_peer_id]["first_name"], "Crypto Sponsor")


if __name__ == "__main__":
    unittest.main()
