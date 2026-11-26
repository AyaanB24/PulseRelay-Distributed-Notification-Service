# PulseRelay Frontend

Professional SaaS dashboard for the PulseRelay Distributed Notification Service.

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling
- **shadcn/ui** - Component library
- **Axios** - HTTP client
- **Recharts** - Data visualization
- **React Router** - Routing

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create `.env` file with backend URL:
   ```
   VITE_API_BASE_URL=http://localhost:8084/api
   ```

3. Start development server:
   ```bash
   npm run dev
   ```

4. Build for production:
   ```bash
   npm run build
   ```

## Project Structure

```
src/
├── api/              # API client and service definitions
├── components/       # Reusable React components
├── pages/            # Page components
├── hooks/            # Custom React hooks
├── types/            # TypeScript types and interfaces
├── utils/            # Utility functions
├── context/          # React context providers
├── App.tsx           # Root component
└── main.tsx          # Entry point
```

## API Integration

All API calls go through the centralized `apiClient` configured in `src/api/client.ts`.

API modules:
- `tenant.ts` - Tenant management and registration
- `notifications.ts` - Notification submission and testing
- `templates.ts` - Notification template management
- `routingPolicies.ts` - Routing policy configuration

## Development

- **Linting**: `npm run lint`
- **Type checking**: `npm run type-check`
- **Preview build**: `npm run preview`

## Design

- Professional, minimalist design
- Neutral color palette (white, gray)
- Responsive layouts (desktop, tablet, mobile)
- Clear visual hierarchy
- No excessive animations
