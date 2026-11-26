# PulseRelay Frontend

Professional SaaS notification dashboard frontend for the PulseRelay Distributed Notification Service.

## Overview

This is a **Phase 01** foundation project. The frontend provides:
- Authentication with API key
- Responsive sidebar navigation
- Clean, minimalist UI with Tailwind CSS
- Type-safe API client with Axios
- Routes and structure for future feature development

## Quick Start

### Prerequisites
- Node.js 18+
- npm 9+
- Backend running on `http://localhost:8084`

### Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Navigate to http://localhost:5173
```

### Build for Production

```bash
npm run build
npm run preview
```

## Project Structure

```
src/
├── components/      # Reusable UI components (Sidebar, TopNav)
├── config/         # Configuration (API endpoints, settings)
├── hooks/          # Custom React hooks (useAuth)
├── pages/          # Full-page components
├── services/       # Business logic (HTTP client)
├── types/          # TypeScript type definitions
├── App.tsx         # Root component with routing
├── main.tsx        # Entry point
└── index.css       # Global styles
```

## Available Scripts

```bash
npm run dev         # Start dev server (Vite HMR)
npm run build       # Build for production
npm run preview     # Preview production build
npm run lint        # Run ESLint
npm run type-check  # Run TypeScript check
```

## Environment Variables

Create a `.env` file in the root (see `.env.example`):

```
VITE_API_BASE_URL=http://localhost:8084
VITE_API_CONTEXT_PATH=/api
```

## Authentication

1. Navigate to `/login`
2. Enter your API key (from backend tenant registration)
3. Submit form
4. API key stored in localStorage, automatically added to all requests

## Features

### Phase 01 (Current)
- ✅ Login page with API key authentication
- ✅ Dashboard with tenant information
- ✅ Responsive sidebar navigation
- ✅ Centralized API client with interceptors
- ✅ Type-safe API communication
- ✅ Protected routes

### Upcoming Phases
- Phase 02: Templates management
- Phase 03: Routing policies management
- Phase 04: Test delivery interface
- Phase 05+: Analytics, webhooks, monitoring

## API Integration

All API calls go through centralized `httpClient`:

```typescript
import { httpClient } from './services/http';

// API key automatically added to headers
const tenantInfo = await httpClient.get('/v1/tenant/me');
```

For detailed API documentation, see `../BACKEND_API_AUDIT.md`

## Styling

Uses **Tailwind CSS** with:
- Slate color palette (neutral grays)
- Custom component utilities in `@layer components`
- Mobile-first responsive design
- No external UI library dependencies

## Code Quality

- **TypeScript** strict mode enabled
- **ESLint** for code consistency
- **Vite** for fast builds
- Production build: 75KB JS + 14KB CSS (gzipped)

## Troubleshooting

### "Cannot connect to API"
- Ensure backend is running on `http://localhost:8084`
- Check `VITE_API_BASE_URL` in `.env`
- Check browser DevTools Network tab

### "401 Unauthorized"
- API key may be invalid or revoked
- Try re-logging in with correct key
- Check localStorage has `apiKey`

### TypeScript Errors
```bash
npm run type-check
```

### Build Errors
```bash
npm run lint
npm run build
```

## Development Tips

1. **Hot Module Reload (HMR)**: Changes automatically reload in browser
2. **API Calls**: Use `httpClient` for consistent authentication
3. **Components**: Place in `src/components`, pages in `src/pages`
4. **Types**: Define in `src/types/api.ts` for API contracts
5. **Styling**: Add Tailwind classes or use `@layer` utilities

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## License

Private - All rights reserved

---

For full implementation details, see `docs/phase01.md`
