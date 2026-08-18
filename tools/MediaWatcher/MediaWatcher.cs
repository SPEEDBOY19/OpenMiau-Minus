using System;
using System.IO;
using System.Text.Json;
using System.Threading;
using Windows.Media.Control;

class MediaWatcher
{
    static readonly string OutputPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "miau", "nowplaying.json");

    static async System.Threading.Tasks.Task Main(string[] args)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(OutputPath)!);

        int pollMs = 1000;
        if (args.Length > 0 && int.TryParse(args[0], out int customPoll))
            pollMs = Math.Max(250, customPoll);

        Console.WriteLine($"[MediaWatcher] Writing state to {OutputPath}");
        Console.WriteLine($"[MediaWatcher] Poll interval: {pollMs}ms");

        while (true)
        {
            try
            {
                await PollOnce();
            }
            catch (Exception ex)
            {
                WriteError(ex.Message);
                Console.Error.WriteLine($"[MediaWatcher] Error: {ex}");
            }

            Thread.Sleep(pollMs);
        }
    }

    static async System.Threading.Tasks.Task PollOnce()
    {
        var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        var session = manager.GetCurrentSession();

        if (session == null)
        {
            WriteState(new NowPlayingState { active = false });
            return;
        }

        var props = await session.TryGetMediaPropertiesAsync();
        var timeline = session.GetTimelineProperties();
        var playback = session.GetPlaybackInfo();

        bool isPlaying = playback?.PlaybackStatus ==
            GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;

        var state = new NowPlayingState
        {
            active = isPlaying,
            title = props?.Title ?? "Unknown",
            artist = props?.Artist ?? "",
            platform = session.SourceAppUserModelId ?? "Unknown",
            positionSeconds = (int)timeline.Position.TotalSeconds,
            durationSeconds = (int)timeline.EndTime.TotalSeconds,
            isPlaying = isPlaying,
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };

        WriteState(state);
    }

    static void WriteState(NowPlayingState state)
    {
        try
        {
            string json = JsonSerializer.Serialize(state);
            string tmp = OutputPath + ".tmp";
            File.WriteAllText(tmp, json);
            File.Copy(tmp, OutputPath, overwrite: true);
            File.Delete(tmp);
        }
        catch (IOException)
        {
            // File locked by reader mid-write; skip this cycle, next poll retries.
        }
    }

    static void WriteError(string message)
    {
        WriteState(new NowPlayingState { active = false, error = message });
    }
}

class NowPlayingState
{
    public bool active { get; set; }
    public string title { get; set; } = "";
    public string artist { get; set; } = "";
    public string platform { get; set; } = "";
    public int positionSeconds { get; set; }
    public int durationSeconds { get; set; }
    public bool isPlaying { get; set; }
    public long timestamp { get; set; }
    public string? error { get; set; }
}