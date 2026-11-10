import platform, sys, traceback
import gymnasium as gym, highway_env
print(f"python={sys.version.split()[0]} os={platform.platform()} highway_env={highway_env.__version__}")
for env_id in ["highway-v0", "merge-v0", "intersection-v0", "roundabout-v0"]:
    try:
        env = gym.make(env_id, render_mode="rgb_array")
        obs, _ = env.reset(seed=0)
        steps, done = 0, False
        while not done and steps < 30:
            obs, r, term, trunc, _ = env.step(env.action_space.sample())
            done = term or trunc; steps += 1
        img = env.render()
        print(f"PASS {env_id}: steps={steps} vehicles={len(env.unwrapped.road.vehicles)} render={img.shape}")
        env.close()
    except Exception as e:
        print(f"FAIL {env_id}: {type(e).__name__}: {e}")
        traceback.print_exc()
