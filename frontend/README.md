# Calendar Copilot: frontend

Angular 22 app (standalone components, signals, zoneless change detection). See the [project README](../README.md) for the architecture and setup.

```bash
nvm use                      # Node 24
npm install
npm start                    # http://localhost:4200, proxies /api to localhost:8080
npx ng test --watch=false    # unit tests (Vitest)
npx ng build                 # production build to dist/frontend/browser
```
