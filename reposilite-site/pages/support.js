import { Box, Button, Divider, Flex, Heading, Text } from "@chakra-ui/react"
import Head from "next/head"
import { ColorModeStyles, useColorModeValue } from "nextjs-color-mode"
import Layout from "../components/layout/Layout"
import { Link } from "../components/Link"
import { chakraColor } from "../helpers/chakra-theme"

const individualCards = [
  { 
    title: 'Star project',
    description: 'Star the project on GitHub to help us reach a wider audience',
    buttons: [
      {
        title: 'Star Ingot',
        link: 'https://github.com/OneLiteFeatherNET/ingot/stargazers'
      }
    ]
  },
  {
    title: 'Support upstream',
    description: 'Ingot is a fork of Reposilite. Its author still carries most of the work this project is built on',
    buttons: [
      {
        title: 'Sponsor @dzikoysk',
        link: 'https://github.com/sponsors/dzikoysk'
      },
      {
        title: 'Star Reposilite',
        link: 'https://github.com/dzikoysk/reposilite/stargazers'
      }
    ]
  },
  {
    title: 'Contribute',
    description: 'Report what breaks, or pick up an issue and send a pull request',
    buttons: [
      {
        title: 'Visit issues',
        link: 'https://github.com/OneLiteFeatherNET/ingot/issues'
      },
      {
        title: 'Visit PRs',
        link: 'https://github.com/OneLiteFeatherNET/ingot/pulls'
      }
    ]
  }
]

const IndividualCard = ({ title, description, buttons }) => {
  const [cardBg, cardBgCss] = useColorModeValue('individual-card-bg', chakraColor('gray.100'), chakraColor('gray.900'))
  const [cardButtonBg, cardButtonBgCss] = useColorModeValue('individual-card-button-bg', chakraColor('gray.200'), chakraColor('gray.700'))
  const [cardColor, cardColorCss] = useColorModeValue('individual-card-color', chakraColor('black'), chakraColor('gray.100'))

  return (
    <>
      <ColorModeStyles styles={[cardBgCss, cardButtonBgCss, cardColorCss]} />
      <Flex
        flexDirection={'column'}
        justifyContent={'space-between'}
        backgroundColor={cardBg}
        paddingX={8}
        paddingY={6}
        marginX={4}
        borderRadius={'lg'}
        w={'30%'}
      >
        <Heading as='h2' size={'sm'} textAlign={'center'}>{title}</Heading>
        <Box marginY={4} textAlign={'center'}>{description}</Box>
        {buttons.map(button => (
          <Button
            key={button.title}
            marginTop={2}
            color={cardColor}
            backgroundColor={cardButtonBg}
            _hover={{ backgroundColor: cardButtonBg }}
          >
            <Link href={button.link}>{button.title}</Link>
          </Button>
        ))}
      </Flex>
    </>
  )
}

const organizationCards = [
  {
    title: 'Sponsor',
    description: (
      <Box>
        If you'd like to invest into the open source sector, get in touch through the&nbsp;
        <Link href={'https://github.com/OneLiteFeatherNET/ingot/issues'} color={'purple.400'}>issue tracker</Link>.
        We're open to discussing possibilities individually, so we can find the best arrangement for both sides.
      </Box>
    )
  }
]

const OrganizationCard = ({ title, description }) => {
  const [cardBg, cardBgCss] = useColorModeValue('org-card-bg', chakraColor('gray.100'), chakraColor('gray.900'))

  return (
    <>
      <ColorModeStyles styles={[cardBgCss]} />
      <Flex backgroundColor={cardBg} padding={6} marginBottom={8} w='full' flexDirection={'column'}>
        <Heading as='h2' size={'sm'}>{title}</Heading>
        <Box paddingTop={3}>{description}</Box>
      </Flex>
    </>
  )
}

export default function Home() {
  return (
    <Layout>
      <Head>
        <title>Support · Ingot</title>  
      </Head>
      <Flex flexDirection={'column'} maxW={'container.lg'} px={'10'} mx={'auto'}>
        <Flex flexDirection={'column'} textAlign={'center'} justifyContent={'center'} paddingTop={14} paddingBottom={8}>
          <Heading  as={'h1'} size={'lg'}>How to help? 💕</Heading>
          <Box paddingTop={3}>
            Ingot is a fully open source project, maintained by&nbsp;
            <Link color={'purple.400'} href={'https://github.com/OneLiteFeatherNET'}>OneLiteFeather</Link>.
            <br />
            It is a fork of&nbsp;
            <Link color={'purple.400'} href={'https://github.com/dzikoysk/reposilite'}>Reposilite</Link>
            &nbsp;by <Link color={'purple.400'} href={'https://github.com/dzikoysk'}>@dzikoysk</Link>, licensed under the Apache License 2.0.
          </Box>
        </Flex>
        <Heading textAlign={'center'} size={'md'} paddingBottom={10}>For individuals</Heading>
        <Flex justifyContent={'space-between'}>
          {individualCards.map(card => (
            <IndividualCard key={card.title} {...card} />
          ))}
        </Flex>
        <Heading textAlign={'center'} size={'md'} paddingTop={10} paddingBottom={12}>For organizations</Heading>
        <Flex flexDirection={'column'}>
          {organizationCards.map(card => (
            <OrganizationCard key={card.title} {...card} />
          ))}
        </Flex>
      </Flex>
    </Layout>
  )
}