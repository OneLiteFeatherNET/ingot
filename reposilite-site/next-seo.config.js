const URL = process.env.NODE_ENV === "production"
  ? process.env.SITE_URL
  : "http://localhost:3000/"

export default {
  title: 'Ingot',
  description: 'Lightweight and easy-to-use repository manager for Maven based artifacts in JVM ecosystem. This is simple, extensible and scalable self-hosted solution to replace managers like Nexus, Archiva or Artifactory, with reduced resources consumption.',
  openGraph: {
    type: 'website',
    locale: 'en_US',
    url: URL,
    site_name: 'Ingot',
    // TODO(onelitefeather): social preview image for Ingot. The upstream one was dropped
    // rather than reused, because it carries the Reposilite branding.
  },
  twitter: {
    cardType: 'summary'
  },
};