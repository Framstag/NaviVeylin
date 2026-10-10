# Tone and wording for the catalogue

Referenced by `area-section.md`. Editing this file changes the prompt digest, which invalidates every area's
cached section — that is intended: a tone change must regenerate the prose that was written in the old tone.

## Register

The catalogue is read by someone deciding whether the app does what they need. It is not a changelog, not a
specification, and not an advert. Write like a competent person describing their own app to a stranger:
confident, concrete, and never selling.

## The keyword

The bold keyword of an entry is the one thing a reader scans for across the whole area. Six words at most.
Write it as a promise about the reader's situation, not as a label:

| rather than | write |
|---|---|
| Routing | Know the road before you go |
| Offline maps | Works without a connection |
| Favourites | The places you keep |
| Search | Find anywhere by name |
| Navigation display | Follow the turn |
| Settings | Set it up your way |

A keyword never contains a spec id, a mechanism, or a word the cited specs do not contain.

## Verbs

Prefer the verb the user performs or the app performs for them.

| rather than | write |
|---|---|
| "Supports zoom gestures" | "Pinch to zoom, and the map follows your fingers" |
| "Provides a details dialog" | "Tap any street or building to see what it is" |
| "Lets users manage favorite groups" | "Group your favorites and colour the groups" |
| "Renders lane guidance" | "Know which lane to be in at a junction" |
| "Bearing-aware road lookup is implemented" | "The street name on screen is the one you are driving on" |

## Words that earn their place

*offline* — the maps work without a connection, which is the app's central claim.
*screen*, *phone*, *car* — say where a thing happens when it only happens there.
*without* — a benefit is usually the absence of something: without a connection, without touching the phone.

## Spelling and vocabulary follow the project, not the writer

The specs, the UI strings and the code are **American English**, and this document must read as part of the same
product: *favorite*, *color*, *center*, *license*, *meter*, *traveling*. A British spelling is not a style choice
here, it is an inconsistency a reader will notice, and the run's word report will list every one of them because
they appear in none of the specs.

More generally: **use the nouns the specs use.** When the requirement text says *favorite*, *map manager* or
*details sheet*, those are the product's words; a synonym you prefer is a word the reader has to map back. The
run reports the words it cannot find in the cited specs — treat that list as a spelling and vocabulary check
you were asked to pass, not as noise.

## Words to avoid

*seamless*, *effortless*, *next-generation*, *cutting-edge*, *powerful*, *simply*, *just* — none of them say
anything checkable, and each one costs the reader a line.

*robust*, *optimised*, *lightweight* — these are claims about the implementation, and the catalogue is about
what the app does.

## Numbers

A figure is the fastest way to make a bullet checkable and the fastest way to make it false. A figure the run
cannot find in a cited spec fails the whole run, so state only figures the capability text states, and prefer
to leave it out when the text does not have one.
