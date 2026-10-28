import {defineConfig} from 'vite';
import {devtools} from '@tanstack/devtools-vite';
import {tanstackRouter} from '@tanstack/router-plugin/vite';
import viteReact from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const config = defineConfig({
	resolve: {tsconfigPaths: true},
	plugins: [
		devtools(),
		tailwindcss(),
		tanstackRouter({target: 'react', autoCodeSplitting: true}),
		viteReact()
	],
	server: {
		// The backend serves the API and the streams. Set its public-url to this dev server, so logging in returns here.
		proxy: {
			'/api': 'http://localhost:6742',
			'/live': 'http://localhost:6742'
		}
	}
});

export default config;
