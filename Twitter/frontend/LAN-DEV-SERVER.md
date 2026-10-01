# Open the dev app on your phone (LAN dev server)

Written for: developers who want to try the frontend on a real phone while it runs on their Mac.

The Vite dev server can listen on your local network, so a phone on the same Wi-Fi opens the live app. You get hot
reload and real login against your local gateway, with nothing to deploy.

## Steps

1. **Start the gateway** on the Mac (it listens on `:8080`). Check it with
   `curl -i http://localhost:8080/api/v1/auth/me`. A `401` means it is up.
2. **Start the dev server with `--host`:**

   ```bash
   cd Twitter/frontend
   npm run dev -- --host
   ```

   Vite prints a `Network:` line, for example `http://172.20.10.2:5173/`.
3. **Open that address in the phone's browser** (use `/login` or `/register` as the path).

If port 5173 is already taken by another dev server, Vite quietly picks the next free one and prints it. To pin a
port and fail loudly instead, add `--port 5174 --strictPort`. The address I used was
`http://172.20.10.2:5174/login`.

To find the Mac's address yourself: `ipconfig getifaddr en0` (try `en1` if that prints nothing).

## Why the login works from the phone

- **One origin.** The page and the API both load from the Mac's address. `VITE_BASE_API_URL` is empty, so the app
  calls relative `/api/...` paths. Vite's proxy (`vite.config.ts`) forwards them to the gateway on the Mac, so the
  phone never needs to reach `:8080`, and there is no CORS to configure.
- **The session cookie is accepted over plain HTTP.** The gateway sets it with `COOKIE_SECURE=false` locally. In a
  production setup with HTTPS, that flag must be `true`.
- **The proxy target** is read from `GATEWAY_URL` (default `http://localhost:8080`). Set it if the gateway runs
  elsewhere: `GATEWAY_URL=http://other-host:8080 npm run dev -- --host`.

## If the phone can't connect

- The phone and the Mac must be on the same network. A guest Wi-Fi that isolates devices will block it.
- The macOS firewall may ask whether Node may accept incoming connections. Choose **Allow**.
- The address changes when you join another network. Read the `Network:` line again after switching.
- A blank dark screen for a moment on load is normal: the app is asking the gateway whether you are logged in.

## Stop it

Press `Ctrl+C` in the terminal that runs it. The server is reachable by anyone on the same network while it runs, so
stop it when you are done, and don't use it on a network you don't trust.
