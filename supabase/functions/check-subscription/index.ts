import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4"

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") || "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || ""
)

serve(async (req) => {
  try {
    const { userId, deviceId } = await req.json()

    if (!userId) {
      return new Response(
        JSON.stringify({ error: "User ID required" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("subscription_status, subscription_end, trial_end, registered_device_id, has_used_trial")
      .eq("id", userId)
      .maybeSingle()

    if (profileError) {
      return new Response(
        JSON.stringify({ error: profileError.message }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      )
    }

    const now = new Date()

    // VERIFICA TRIAL (apenas se NUNCA usou trial antes)
    let isTrialActive = false
    const hasUsedTrial = profile?.has_used_trial === true
    const hasValidTrialEnd = profile?.trial_end && new Date(profile.trial_end) > now

    if (!hasUsedTrial && profile?.subscription_status === "trial" && hasValidTrialEnd) {
      isTrialActive = true
    }

    // VERIFICA ASSINATURA PAGA
    let isSubscriptionActive = false
    if (profile?.subscription_status === "active" && profile?.subscription_end) {
      const subscriptionEnd = new Date(profile.subscription_end)
      if (subscriptionEnd > now) {
        isSubscriptionActive = true
      }
    }

    const isActive = isTrialActive || isSubscriptionActive

    // DEFINE O PLAN_TYPE
    let displayPlanType = "expired"
    if (isTrialActive) {
      displayPlanType = "trial"
    } else if (isSubscriptionActive) {
      const { data: subscription } = await supabase
        .from("subscriptions")
        .select("plan_type")
        .eq("user_id", userId)
        .eq("status", "active")
        .order("id", { ascending: false })
        .limit(1)
        .maybeSingle()

      displayPlanType = subscription?.plan_type || "active"
    }

    if (!isActive && profile?.subscription_status === "expired") {
      await supabase
        .from("profiles")
        .update({ has_used_trial: true })
        .eq("id", userId)
    }

    console.log(`User ${userId}: isActive=${isActive}, planType=${displayPlanType}, hasUsedTrial=${hasUsedTrial}`)

    return new Response(
      JSON.stringify({
        isActive,
        planType: displayPlanType,
        subscriptionStatus: profile?.subscription_status,
        subscriptionEnd: profile?.subscription_end,
        trialEnd: profile?.trial_end,
        hasUsedTrial,
        isDeviceValid: true,
        activeDevices: "1",
        maxDevices: "1"
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    )
  } catch (error) {
    console.error("Error:", error)
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }
})
