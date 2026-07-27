ui.configure({
    input = "cursor",
    hide_with_gui = true,
    screen_layer = "below",
    priority = 100
})

local root = ui.root()
root.pointer_events = false

local speed = ui.text({
    width = 118,
    height = 18,
    text_align = "right",
    vertical_align = "center",
    font_size = 13,
    color = "#FFF2FBFF",
    shadow = true
})

local health = ui.progress({
    width = 118,
    height = 5,
    min = 0,
    max = 20,
    value = 20,
    track_color = "#7A111820",
    fill_color = "#FF37D7B2"
})

local mode = ui.button({
    width = 78,
    height = 18,
    text = "HUD ACTIVE",
    font_size = 8,
    click_sound = "minecraft:ui.button.click",
    hover_sound = "minecraft:block.amethyst_block.chime",
    sound_volume = 0.55,
    sound_pitch = 1.35,
    on_click = function()
        ui.sound("minecraft:block.note_block.pling", 0.7, 1.6)
    end
})

local card = ui.panel({
    id = "environment_card",
    position = "absolute",
    x = "72%",
    y = "70%",
    width = 148,
    height = 78,
    padding = 10,
    layout = "column",
    align = "end",
    gap = 5,
    background = "#B5121B26",
    border = "#A73DD8E8",
    border_width = 1,
    radius = 5,
    pointer_events = true
}, {
    ui.text({
        width = 118,
        height = 10,
        text = "MORPHE · ENV HUD",
        text_align = "right",
        color = "#FF62E7F5",
        shadow = true
    }),
    speed,
    health,
    mode
})

ui.mount(card)

ui.bind(speed, "text", function()
    local blocks_per_second = (env.player.horizontal_speed or 0) * 20
    return string.format("%05.1f m/s", blocks_per_second)
end)

ui.bind(health, "value", function()
    return env.player.health or 0
end)

ui.bind(health, "max", function()
    return env.player.max_health or 20
end)

ui.bind(card, "translate_x", function()
    return (env.motion.sway_x or 0) + (env.motion.bob_x or 0)
end)

ui.bind(card, "translate_y", function()
    return (env.motion.sway_y or 0) + (env.motion.bob_y or 0)
end)

card:keyframes({
    { at = 0.0, opacity = 0, scale = 0.85, translate_y = 18 },
    { at = 0.7, opacity = 1, scale = 1.04, translate_y = -2, easing = "back_out" },
    { at = 1.0, opacity = 1, scale = 1.0, translate_y = 0, easing = "ease_out" }
}, {
    duration = 0.65,
    easing = "out_cubic"
})
