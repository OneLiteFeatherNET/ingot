import { Box, Container, Flex, Heading, Spacer, VStack, Text } from "@chakra-ui/react"
import { Link } from '../../components/Link'
import { ColorModeStyles, useColorModeValue } from "nextjs-color-mode"
import { chakraColor } from "../../helpers/chakra-theme"

const link = (title, url) =>
  ({ title, url: url || "/" })

const guideLinks = [
  link('Getting Started', '/guide/about'),
  link('Installation', '/guide/general'),
  link('Plugins', '/plugin'),
  link('Developer API', '/guide/sources'),
]

// TODO(onelitefeather): community chat link once one exists for Ingot. The upstream
// Discord and Twitter were removed rather than kept, because they are not our channels.
const communityLinks = [
  link('Report Issue', 'https://github.com/OneLiteFeatherNET/ingot/issues/new/choose'),
  link('Go to GitHub', 'https://github.com/OneLiteFeatherNET/ingot'),
  link('Upstream Reposilite', 'https://github.com/dzikoysk/reposilite'),
]

export default function Footer() {
  const [footerBg, footerBgCss] = useColorModeValue('footer-bg', chakraColor('gray.100'), chakraColor('gray.900'))

  return (
    <>
      <ColorModeStyles styles={[footerBgCss ]} />
      <Box style={{ backgroundColor: footerBg }} marginTop={'12'}>
        <Container>
          <Flex justifyContent={'center'} paddingY={10}>
            <VStack>
              <Heading fontSize={16}>Guide</Heading>
              {guideLinks.map(({ title, url }) => (
                <Link key={title} href={url} fontSize={'sm'}>{title}</Link>
              ))}
            </VStack>
            <Spacer />
            <VStack>
              <Heading fontSize={16}>Community</Heading>
              {communityLinks.map(({ title, url }) => (
                <Link key={title} href={url} fontSize={'sm'}>{title}</Link>
              ))}
            </VStack>
          </Flex>
          <Text textAlign={'center'} paddingY='4' fontSize={'sm'}>
            Copyright © 2023 dzikoysk, © 2026 OneLiteFeather · Apache License 2.0
          </Text>
        </Container>
      </Box>
    </>
  )
}
